#!/usr/bin/env python3
"""Fetch a pinned Apache-2.0 Vosk model into ignored generated Android assets."""
import hashlib
from pathlib import Path
import shutil
import urllib.request

MODEL_URL = 'https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip'
MODEL_SHA256 = '30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498'
target = Path(__file__).resolve().parents[1] / 'app/build/generated/speech-assets/speech-model.zip'

def valid(path):
    return path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == MODEL_SHA256

if not valid(target):
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix('.download')
    try:
        with urllib.request.urlopen(MODEL_URL, timeout=120) as response, temporary.open('wb') as output:
            shutil.copyfileobj(response, output)
        if not valid(temporary):
            raise RuntimeError('Speech model checksum mismatch')
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)
print('Verified offline English speech model')
