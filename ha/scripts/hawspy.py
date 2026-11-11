import asyncio, json, os, sys, websockets

URL = "ws://192.168.0.11:8123/api/websocket"

class HA:
    async def __aenter__(self):
        self.ws = await websockets.connect(URL, max_size=None)
        await self.ws.recv()
        await self.ws.send(json.dumps({"type": "auth", "access_token": os.environ["HA_TOKEN"]}))
        r = json.loads(await self.ws.recv())
        assert r["type"] == "auth_ok", r
        self.i = 0
        return self
    async def __aexit__(self, *a):
        await self.ws.close()
    async def call(self, **msg):
        self.i += 1
        msg["id"] = self.i
        await self.ws.send(json.dumps(msg))
        while True:
            r = json.loads(await self.ws.recv())
            if r.get("id") == self.i:
                return r
