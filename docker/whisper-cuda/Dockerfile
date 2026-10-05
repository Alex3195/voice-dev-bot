# whisper.cpp server, CUDA bilan, AVX-512'siz CPU'lar uchun.
#
# Rasmiy ghcr.io/ggml-org/whisper.cpp:main-cuda GGML_NATIVE bilan (CI mashinasining AVX-512'i)
# yig'ilgan — AVX2'li CPU'da (masalan i5-10400F) model yuklanishida SIGILL (exit 132) bilan yiqiladi.
# Bu yerda CPU qismi umumiy AVX2 uchun, GPU qismi faqat kerakli arxitektura uchun yig'iladi.
#
#   docker build -t voice-dev-bot/whisper-cuda docker/whisper-cuda
#   docker build --build-arg CUDA_ARCH=89 ...   # RTX 40xx; 86 — RTX 30xx

ARG CUDA_VERSION=12.8.1
ARG UBUNTU_VERSION=22.04

FROM nvidia/cuda:${CUDA_VERSION}-devel-ubuntu${UBUNTU_VERSION} AS build
ARG WHISPER_VERSION=v1.9.4
ARG CUDA_ARCH=86

RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential cmake git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN git clone --depth 1 --branch ${WHISPER_VERSION} https://github.com/ggml-org/whisper.cpp /src

# Build konteynerida GPU drayveri yo'q — linker libcuda'ni stub'dan oladi
RUN cmake -S /src -B /src/build \
        -DCMAKE_BUILD_TYPE=Release \
        -DGGML_CUDA=ON \
        -DCMAKE_CUDA_ARCHITECTURES=${CUDA_ARCH} \
        -DGGML_NATIVE=OFF -DGGML_AVX=ON -DGGML_AVX2=ON -DGGML_FMA=ON -DGGML_F16C=ON \
        -DWHISPER_BUILD_TESTS=OFF \
        -DCMAKE_EXE_LINKER_FLAGS=-Wl,--allow-shlib-undefined \
        -DCMAKE_LIBRARY_PATH=/usr/local/cuda/lib64/stubs \
    && cmake --build /src/build --config Release -j"$(nproc)" --target whisper-server \
    && mkdir -p /out/lib \
    && cp /src/build/bin/whisper-server /out/ \
    && find /src/build -name '*.so*' -exec cp -P {} /out/lib/ \;

FROM nvidia/cuda:${CUDA_VERSION}-runtime-ubuntu${UBUNTU_VERSION}

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /out/whisper-server /usr/local/bin/
COPY --from=build /out/lib/ /usr/local/lib/
RUN ldconfig

# Rasmiy image bilan bir xil: compose.yaml'dagi bitta satrli buyruq o'zgarmaydi
ENTRYPOINT ["bash", "-c"]
