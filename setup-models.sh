#!/usr/bin/env bash
set -euo pipefail

# Downloads ONNX models required for local development and testing.
# In Docker, models are downloaded during the build (see Dockerfile).

MODEL_DIR="models/ms-marco-MiniLM-L-6-v2"
BASE_URL="https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main"

if [ -f "$MODEL_DIR/model.onnx" ] && [ -f "$MODEL_DIR/tokenizer.json" ]; then
  echo "Models already present in $MODEL_DIR — skipping download."
  exit 0
fi

echo "Downloading cross-encoder reranking model (ms-marco-MiniLM-L-6-v2)..."
mkdir -p "$MODEL_DIR"
curl -L --progress-bar -o "$MODEL_DIR/model.onnx" "$BASE_URL/onnx/model.onnx"
curl -L --progress-bar -o "$MODEL_DIR/tokenizer.json" "$BASE_URL/tokenizer.json"
echo "Done. Models saved to $MODEL_DIR/"
