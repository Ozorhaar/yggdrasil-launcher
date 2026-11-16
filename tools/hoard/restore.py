#!/usr/bin/env python3
"""Decrypts a Hoard backup (from the Yggdrasil Launcher) to the layout JSON the launcher's
"Restore from a file" accepts. Uses the Mac's private key, ~/yggdrasil-keys/hoard.key,
and asks for its passphrase (in the password manager) when the key is encrypted.

    python3 restore.py ~/backups/yggdrasil-launcher/layout-YYYYmmdd-HHMMSS.ygg > layout.json

Format: "YGGHOARD1\\n" + base64(RSA-OAEP-SHA256 wrapped AES key) + "\\n" + base64(GCM nonce)
+ "\\n" + base64(AES-256-GCM ciphertext with tag). Needs the openssl command and Python's
`cryptography` package (pip install --user cryptography).
"""
import base64
import getpass
import os
import sys

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

raw = open(sys.argv[1], "rb").read()
assert raw.startswith(b"YGGHOARD1\n"), "not a hoard backup"
wrapped, nonce, ct = (base64.b64decode(x) for x in raw[len(b"YGGHOARD1\n"):].split(b"\n")[:3])
pem = open(os.path.expanduser("~/yggdrasil-keys/hoard.key"), "rb").read()
# The key is passphrase-protected (saved in the password manager); prompt on the terminal, never via args.
pw = getpass.getpass("hoard.key passphrase: ").encode() if b"ENCRYPTED" in pem else None
key = serialization.load_pem_private_key(pem, pw)
aes = key.decrypt(wrapped, padding.OAEP(mgf=padding.MGF1(hashes.SHA256()), algorithm=hashes.SHA256(), label=None))
sys.stdout.write(AESGCM(aes).decrypt(nonce, ct, None).decode())
