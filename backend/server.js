const express = require("express");
const cors = require("cors");

const app = express();
app.disable("x-powered-by");
app.use(cors({ origin: process.env.ALLOWED_ORIGIN || "*" }));
app.use(express.json({ limit: "32kb" }));

const PORT = process.env.PORT || 3000;
const API_KEY = process.env.GEMINI_API_KEY;
const MODEL = process.env.GEMINI_MODEL || "gemini-2.5-flash";

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "triton-ai-backend" });
});

app.post("/chat", async (req, res) => {
  try {
    if (!API_KEY) {
      return res.status(500).json({ error: "Backend belum memiliki GEMINI_API_KEY." });
    }

    const message = typeof req.body?.message === "string" ? req.body.message.trim() : "";
    if (!message) return res.status(400).json({ error: "Pesan tidak boleh kosong." });
    if (message.length > 8000) return res.status(413).json({ error: "Pesan terlalu panjang (maksimal 8000 karakter)." });

    const incomingHistory = Array.isArray(req.body?.history) ? req.body.history.slice(-12) : [];
    const contents = [];

    for (const item of incomingHistory) {
      if (!item || !["user", "assistant", "model"].includes(item.role) || typeof item.content !== "string") continue;
      const text = item.content.trim().slice(0, 8000);
      if (!text) continue;
      contents.push({
        role: item.role === "assistant" ? "model" : item.role,
        parts: [{ text }]
      });
    }
    contents.push({ role: "user", parts: [{ text: message }] });

    const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(MODEL)}:generateContent?key=${encodeURIComponent(API_KEY)}`;
    const upstream = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        systemInstruction: {
          parts: [{ text: "Nama kamu Triton AI. Jawab dalam bahasa Indonesia secara jelas, ramah, dan akurat kecuali pengguna meminta bahasa lain. Jika tidak yakin, katakan dengan jujur." }]
        },
        contents,
        generationConfig: { temperature: 0.7, maxOutputTokens: 2048 }
      }),
      signal: AbortSignal.timeout(55000)
    });

    const data = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      const providerMessage = data?.error?.message || `Penyedia AI merespons HTTP ${upstream.status}`;
      return res.status(502).json({ error: providerMessage });
    }

    const reply = (data?.candidates?.[0]?.content?.parts || [])
      .map(part => part.text || "")
      .join("")
      .trim();

    if (!reply) return res.status(502).json({ error: "Penyedia AI tidak mengembalikan teks." });
    res.json({ reply });
  } catch (err) {
    console.error("Chat request failed:", err.message);
    res.status(500).json({ error: "Permintaan gagal. Coba lagi sebentar." });
  }
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Triton AI backend listening on port ${PORT}`);
});
