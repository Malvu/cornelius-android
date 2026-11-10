"""Faux Cornelius pour essayer l'application sur un vrai téléphone, sans toucher au serveur.

Écoute en WSS sur le port 3443 avec un certificat auto-signé, comme Cornelius.
Accepte l'association, puis joue le dialogue d'un satellite et envoie une note.

Le téléphone y accède par `adb reverse tcp:3443 tcp:3443` : il se connecte à
127.0.0.1:3443, qui arrive ici.

    python outils/faux-cornelius.py
"""

import asyncio
import datetime
import json
import math
import ssl
import struct
from pathlib import Path

import websockets
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

DOSSIER = Path(__file__).resolve().parent / "faux-cornelius"
CERT = DOSSIER / "certificat.pem"
CLE = DOSSIER / "cle.pem"
PORT = 3443


def fabriquer_certificat():
    DOSSIER.mkdir(parents=True, exist_ok=True)
    if CERT.is_file() and CLE.is_file():
        return
    cle = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    nom = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Cornelius (test)")])
    maintenant = datetime.datetime.now(datetime.timezone.utc)
    certificat = (
        x509.CertificateBuilder()
        .subject_name(nom)
        .issuer_name(nom)
        .public_key(cle.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(maintenant - datetime.timedelta(days=1))
        .not_valid_after(maintenant + datetime.timedelta(days=30))
        .add_extension(
            x509.SubjectAlternativeName([x509.DNSName("cornelius-test"), x509.IPAddress(
                __import__("ipaddress").ip_address("127.0.0.1"))]),
            critical=False,
        )
        .sign(cle, hashes.SHA256())
    )
    CERT.write_bytes(certificat.public_bytes(serialization.Encoding.PEM))
    CLE.write_bytes(
        cle.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
    )


def note(frequence=440.0, duree=1.5, echantillons=16000):
    total = int(duree * echantillons)
    return struct.pack(
        "<%dh" % total,
        *(int(9000 * math.sin(2 * math.pi * frequence * i / echantillons)) for i in range(total)),
    )


async def traiter(websocket):
    chemin = getattr(websocket, "path", None) or websocket.request.path
    print(f"connexion {chemin}", flush=True)

    if chemin.startswith("/satellite/pair"):
        message = json.loads(await websocket.recv())
        print(f"  pair : {message}", flush=True)
        await websocket.send(json.dumps({"type": "waiting"}))
        await asyncio.sleep(2)
        await websocket.send(
            json.dumps({"type": "paired", "name": "Téléphone de test", "token": "jeton-de-test"})
        )
        print("  associé", flush=True)
        await asyncio.sleep(1)
        return

    if chemin.startswith("/satellite"):
        message = json.loads(await websocket.recv())
        print(f"  hello : {message}", flush=True)
        await websocket.send(
            json.dumps({"type": "welcome", "protocol": 1, "id": "sat-test",
                        "name": "Téléphone de test", "volume": 70})
        )
        await websocket.send(
            json.dumps({"type": "settings", "volume": 70,
                        "led": {"color": "#00ffb4", "brightness": 6, "nightLight": False},
                        "attentive": False})
        )
        await websocket.send(json.dumps({"type": "listen", "enabled": True}))
        print("  bienvenue envoyée", flush=True)

        async def lire():
            trames = 0
            octets = 0
            try:
                async for message in websocket:
                    if isinstance(message, bytes):
                        trames += 1
                        octets += len(message)
                        if trames % 25 == 0:
                            print(f"  reçu {trames} trames ({octets} octets, "
                                  f"dernière = {len(message)})", flush=True)
                    else:
                        print(f"  message : {message}", flush=True)
            except websockets.ConnectionClosed:
                pass
            print(f"  fin : {trames} trames, {octets} octets", flush=True)

        tache = asyncio.create_task(lire())

        await asyncio.sleep(3)
        await websocket.send(json.dumps({"type": "wake", "score": 0.93}))
        await websocket.send(json.dumps({"type": "session", "state": "connecting"}))
        await asyncio.sleep(1)
        await websocket.send(json.dumps({"type": "session", "state": "ready"}))
        print("  conversation ouverte, envoi d'une note", flush=True)
        audio = note()
        for debut in range(0, len(audio), 3200):
            await websocket.send(audio[debut:debut + 3200])
            await asyncio.sleep(0.1)
        await asyncio.sleep(2)
        await websocket.send(json.dumps({"type": "session", "state": "closed"}))
        print("  conversation fermée", flush=True)
        await asyncio.wait([tache], timeout=60)
        return


async def main():
    fabriquer_certificat()
    contexte = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    contexte.load_cert_chain(str(CERT), str(CLE))
    async with websockets.serve(traiter, "0.0.0.0", PORT, ssl=contexte):
        print(f"faux Cornelius sur wss://0.0.0.0:{PORT}", flush=True)
        await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
