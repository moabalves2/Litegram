package org.telegram.messenger;

import android.app.Activity;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Litegram "Baixar Música": resolve YouTube / YouTube Music via API pública
 * (instâncias Piped) e baixa o áudio direto da stream googlevideo (sem
 * re-upload manual), enviando como áudio .m4a ~128kbps com metadata
 * (título/autor) e sem capa.
 */
public final class YtMusicDownloader {

    /** Instâncias públicas Piped (fallback em ordem). */
    private static final String[] PIPED_API = {
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://pipedapi.reallyaweso.me",
        "https://pipedapi.leptons.xyz"
    };

    public interface Callback {
        void onOk(File audioFile, String title, String author, int durationSec);
        void onError(String msg);
    }

    private YtMusicDownloader() {}

    public static void downloadAndSend(Activity activity, int currentAccount, long dialogId,
                                       String queryOrUrl, Callback cb) {
        new Thread(() -> {
            try {
                String videoId = extractVideoId(queryOrUrl);
                String titleHint = null;
                if (videoId == null) {
                    // Pesquisa: usa primeiro resultado de vídeo
                    SearchResult sr = searchFirst(queryOrUrl);
                    if (sr == null) throw new Exception("Nada encontrado");
                    videoId = sr.videoId;
                    titleHint = sr.title;
                }
                StreamInfo info = fetchStream(videoId);
                if (info.audioUrl == null) throw new Exception("Sem stream de áudio");
                String title = info.title != null ? info.title : (titleHint != null ? titleHint : videoId);
                File out = new File(ApplicationLoader.applicationContext.getCacheDir(),
                        "litegram_yt_" + videoId + ".m4a");
                downloadToFile(info.audioUrl, out);
                String author = info.author != null ? info.author : "YouTube";
                int dur = info.durationSec;
                AndroidUtilities.runOnUIThread(() -> {
                    // Monta como áudio (música) com metadata, sem capa
                    try {
                        TLRPC.TL_message message = new TLRPC.TL_message();
                        message.out = true;
                        message.id = 0;
                        message.peer_id = MessagesController.getInstance(currentAccount).getPeer(dialogId);
                        message.date = (int) (System.currentTimeMillis() / 1000);
                        message.message = "";
                        message.media = new TLRPC.TL_messageMediaDocument();
                        message.media.document = new TLRPC.TL_document();
                        message.media.document.mime_type = "audio/mp4";
                        message.media.document.size = (int) out.length();
                        message.media.document.dc_id = 0;
                        message.media.document.date = message.date;
                        TLRPC.TL_documentAttributeAudio attr = new TLRPC.TL_documentAttributeAudio();
                        attr.duration = dur;
                        attr.title = title;
                        attr.performer = author;
                        attr.flags |= 3;
                        message.media.document.attributes.add(attr);
                        TLRPC.TL_documentAttributeFilename fn = new TLRPC.TL_documentAttributeFilename();
                        fn.file_name = out.getName();
                        message.media.document.attributes.add(fn);
                        message.attachPath = out.getAbsolutePath();
                        MessageObject msgObj = new MessageObject(currentAccount, message, false, true);
                        java.util.ArrayList<MessageObject> audios = new java.util.ArrayList<>();
                        audios.add(msgObj);
                        SendMessagesHelper.prepareSendingAudioDocuments(
                            AccountInstance.getInstance(currentAccount),
                            audios, null, dialogId, null, null, null, true, 0, 0, null, null, 0, false, 0);
                    } catch (Exception e2) { FileLog.e(e2); }
                    if (cb != null) cb.onOk(out, title, author, dur);
                });
            } catch (Exception e) {
                FileLog.e(e);
                String msg = e.getMessage() != null ? e.getMessage() : "Falha ao baixar";
                AndroidUtilities.runOnUIThread(() -> { if (cb != null) cb.onError(msg); });
            }
        }).start();
    }

    // ---------- YouTube id ----------

    static String extractVideoId(String s) {
        if (s == null) return null;
        s = s.trim();
        try {
            if (s.matches("^[A-Za-z0-9_-]{11}$")) return s;
            Uri u = Uri.parse(s);
            String v = u.getQueryParameter("v");
            if (v != null && v.length() == 11) return v;
            String host = u.getHost() != null ? u.getHost() : "";
            String path = u.getPath() != null ? u.getPath() : "";
            if (host.contains("youtu.be") && path.length() >= 12) {
                return path.substring(1, 12);
            }
            if (path.contains("/shorts/")) {
                String id = path.substring(path.indexOf("/shorts/") + 8);
                if (id.length() >= 11) return id.substring(0, 11);
            }
            if (path.contains("/embed/")) {
                String id = path.substring(path.indexOf("/embed/") + 7);
                if (id.length() >= 11) return id.substring(0, 11);
            }
            // music.youtube.com usa ?v= também (já tratado acima)
        } catch (Exception ignored) {}
        return null;
    }

    private static class SearchResult {
        String videoId; String title; String author; int durationSec;
    }

    /** Pesquisa e retorna até `limit` faixas (YouTube / YT Music). */
    public static void searchTracks(String query, int limit, SearchCallback cb) {
        new Thread(() -> {
            try {
                java.util.ArrayList<SearchResult> out = new java.util.ArrayList<>();
                Exception last = null;
                for (String base : PIPED_API) {
                    try {
                        String url = base + "/search?q=" + URLEncoder.encode(query, "UTF-8") + "&filter=videos";
                        JSONObject j = getJson(url);
                        JSONArray items = j.optJSONArray("items");
                        if (items != null) {
                            for (int i = 0; i < items.length() && out.size() < limit; i++) {
                                JSONObject it = items.optJSONObject(i);
                                if (it == null) continue;
                                String u = it.optString("url", "");
                                String id = extractVideoId("https://youtube.com" + u);
                                if (id == null) id = extractVideoId(u);
                                if (id == null) continue;
                                SearchResult r = new SearchResult();
                                r.videoId = id;
                                r.title = it.optString("title", id);
                                r.author = it.optString("uploaderName", it.optString("uploader", "YouTube"));
                                r.durationSec = (int) it.optLong("duration", 0);
                                out.add(r);
                            }
                        }
                        if (!out.isEmpty()) break;
                    } catch (Exception e) { last = e; }
                }
                if (out.isEmpty() && last != null) throw last;
                java.util.ArrayList<SearchResult> res = out;
                AndroidUtilities.runOnUIThread(() -> cb.onResult(res));
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> cb.onResult(new java.util.ArrayList<>()));
            }
        }).start();
    }

    public interface SearchCallback {
        void onResult(java.util.List<SearchResult> results);
    }

    /**
     * Barrinha de pesquisa + lista de resultados com botões.
     * Toque num resultado baixa e envia como arquivo de áudio normal
     * (mensagem própria, sem bot).
     */
    public static void showPicker(android.content.Context context, int currentAccount, long dialogId,
                                  Runnable onSent) {
        android.widget.LinearLayout root = new android.widget.LinearLayout(context);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(16);
        root.setPadding(pad, AndroidUtilities.dp(8), pad, 0);

        android.widget.LinearLayout row = new android.widget.LinearLayout(context);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.EditText input = new android.widget.EditText(context);
        input.setHint("Nome da música ou link do YouTube");
        input.setSingleLine(true);
        android.widget.LinearLayout.LayoutParams ip = new android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(input, ip);
        android.widget.Button goBtn = new android.widget.Button(context);
        goBtn.setText("Buscar");
        row.addView(goBtn);
        root.addView(row);

        android.widget.TextView status = new android.widget.TextView(context);
        status.setText("Digite e toque em Buscar");
        status.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        root.addView(status);

        android.widget.LinearLayout list = new android.widget.LinearLayout(context);
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.addView(list);
        android.widget.LinearLayout.LayoutParams sp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(300));
        root.addView(scroll, sp);

        org.telegram.ui.ActionBar.AlertDialog dialog = new org.telegram.ui.ActionBar.AlertDialog.Builder(context)
                .setTitle("Baixar Música ♪")
                .setView(root)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();

        Runnable doSearch = () -> {
            String q = input.getText() != null ? input.getText().toString().trim() : "";
            if (q.isEmpty()) return;
            // Link direto: baixa na hora, sem lista
            String directId = extractVideoId(q);
            if (directId != null) {
                status.setText("Baixando…");
                downloadById(context, currentAccount, dialogId, directId, null, new Callback() {
                    @Override public void onOk(File f, String t, String a, int d) {
                        dialog.dismiss();
                        if (onSent != null) onSent.run();
                    }
                    @Override public void onError(String msg) { status.setText("Erro: " + msg); }
                });
                return;
            }
            status.setText("Pesquisando…");
            list.removeAllViews();
            searchTracks(q, 15, results -> {
                list.removeAllViews();
                if (results.isEmpty()) {
                    status.setText("Nada encontrado, tente outro nome");
                    return;
                }
                status.setText(results.size() + " resultado(s) — toque para enviar como áudio");
                for (SearchResult r : results) {
                    android.widget.Button b = new android.widget.Button(context);
                    String dur = r.durationSec > 0
                            ? String.format(" (%d:%02d)", r.durationSec / 60, r.durationSec % 60) : "";
                    b.setText("♪ " + r.title + " — " + r.author + dur);
                    b.setAllCaps(false);
                    b.setOnClickListener(v -> {
                        b.setEnabled(false);
                        b.setText("Baixando " + r.title + "…");
                        downloadById(context, currentAccount, dialogId, r.videoId, r, new Callback() {
                            @Override public void onOk(File f, String t, String a, int d) {
                                dialog.dismiss();
                                if (onSent != null) onSent.run();
                            }
                            @Override public void onError(String msg) {
                                b.setEnabled(true);
                                b.setText("♪ " + r.title + " — " + r.author + dur);
                                status.setText("Erro: " + msg);
                            }
                        });
                    });
                    list.addView(b);
                }
            });
        };
        goBtn.setOnClickListener(v -> doSearch.run());
        input.setOnEditorActionListener((v, actionId, event) -> { doSearch.run(); return true; });

        dialog.show();
    }

    /** Baixa por videoId e envia como áudio normal (sem bot). */
    public static void downloadById(android.content.Context context, int currentAccount, long dialogId,
                                    String videoId, SearchResult hint, Callback cb) {
        new Thread(() -> {
            try {
                StreamInfo info = fetchStream(videoId);
                if (info.audioUrl == null) throw new Exception("Sem stream de áudio");
                String title = info.title != null ? info.title : (hint != null ? hint.title : videoId);
                String author = info.author != null ? info.author : (hint != null ? hint.author : "YouTube");
                int dur = info.durationSec > 0 ? info.durationSec : (hint != null ? hint.durationSec : 0);
                File out = new File(ApplicationLoader.applicationContext.getCacheDir(),
                        "litegram_yt_" + videoId + ".m4a");
                downloadToFile(info.audioUrl, out);
                sendAudioFile(currentAccount, dialogId, out, title, author, dur);
                AndroidUtilities.runOnUIThread(() -> { if (cb != null) cb.onOk(out, title, author, dur); });
            } catch (Exception e) {
                FileLog.e(e);
                String msg = e.getMessage() != null ? e.getMessage() : "Falha ao baixar";
                AndroidUtilities.runOnUIThread(() -> { if (cb != null) cb.onError(msg); });
            }
        }).start();
    }

    /** Envia arquivo como mensagem de áudio própria (conta do usuário, sem bot). */
    private static void sendAudioFile(int currentAccount, long dialogId, File out,
                                      String title, String author, int dur) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.out = true;
        message.id = 0;
        message.peer_id = MessagesController.getInstance(currentAccount).getPeer(dialogId);
        message.date = (int) (System.currentTimeMillis() / 1000);
        message.message = "";
        message.media = new TLRPC.TL_messageMediaDocument();
        message.media.document = new TLRPC.TL_document();
        message.media.document.mime_type = "audio/mp4";
        message.media.document.size = (int) out.length();
        message.media.document.dc_id = 0;
        message.media.document.date = message.date;
        TLRPC.TL_documentAttributeAudio attr = new TLRPC.TL_documentAttributeAudio();
        attr.duration = dur;
        attr.title = title;
        attr.performer = author;
        attr.flags |= 3;
        message.media.document.attributes.add(attr);
        TLRPC.TL_documentAttributeFilename fn = new TLRPC.TL_documentAttributeFilename();
        fn.file_name = out.getName();
        message.media.document.attributes.add(fn);
        message.attachPath = out.getAbsolutePath();
        MessageObject msgObj = new MessageObject(currentAccount, message, false, true);
        java.util.ArrayList<MessageObject> audios = new java.util.ArrayList<>();
        audios.add(msgObj);
        SendMessagesHelper.prepareSendingAudioDocuments(
            AccountInstance.getInstance(currentAccount),
            audios, null, dialogId, null, null, null, true, 0, 0, null, null, 0, false, 0);
    }

    private static SearchResult searchFirst(String query) throws Exception {
        Exception last = null;
        for (String base : PIPED_API) {
            try {
                String url = base + "/search?q=" + URLEncoder.encode(query, "UTF-8") + "&filter=videos";
                JSONObject j = getJson(url);
                JSONArray items = j.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.optJSONObject(i);
                        if (it == null) continue;
                        String u = it.optString("url", "");
                        String id = extractVideoId("https://youtube.com" + u);
                        if (id == null) id = extractVideoId(u);
                        if (id != null) {
                            SearchResult r = new SearchResult();
                            r.videoId = id;
                            r.title = it.optString("title", null);
                            return r;
                        }
                    }
                }
            } catch (Exception e) { last = e; }
        }
        if (last != null) throw last;
        return null;
    }

    private static class StreamInfo {
        String audioUrl; String title; String author; int durationSec;
    }

    /** Escolhe stream de áudio m4a ~128kbps (sem vídeo), URL googlevideo pública. */
    private static StreamInfo fetchStream(String videoId) throws Exception {
        Exception last = null;
        for (String base : PIPED_API) {
            try {
                JSONObject j = getJson(base + "/streams/" + videoId);
                StreamInfo si = new StreamInfo();
                si.title = j.optString("title", null);
                si.author = j.optString("uploader", null);
                si.durationSec = (int) j.optLong("duration", 0);
                JSONArray audios = j.optJSONArray("audioStreams");
                String best = null; int bestScore = Integer.MAX_VALUE;
                String fallback = null;
                if (audios != null) {
                    for (int i = 0; i < audios.length(); i++) {
                        JSONObject a = audios.optJSONObject(i);
                        if (a == null) continue;
                        String url = a.optString("url", "");
                        if (url.isEmpty()) continue;
                        String codec = a.optString("codec", "").toLowerCase();
                        int br = a.optInt("bitrate", 0); // kbps aprox
                        if (fallback == null) fallback = url;
                        if (codec.contains("m4a") || codec.contains("mp4a") || url.contains("mime=audio%2Fmp4") || url.contains("mime=audio/mp4")) {
                            int score = Math.abs(br - 128);
                            if (score < bestScore) { bestScore = score; best = url; }
                        }
                    }
                }
                si.audioUrl = best != null ? best : fallback;
                if (si.audioUrl != null) return si;
            } catch (Exception e) { last = e; }
        }
        if (last != null) throw last;
        throw new Exception("Sem stream de áudio");
    }

    // ---------- HTTP ----------

    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "Litegram/1.0");
        InputStream in = new BufferedInputStream(c.getInputStream());
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
        in.close();
        return new JSONObject(sb.toString());
    }

    /** Baixa da googlevideo direto pro cache (stream, sem carregar tudo na RAM). */
    private static void downloadToFile(String url, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "Litegram/1.0");
        InputStream in = new BufferedInputStream(c.getInputStream());
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[32768];
        int n;
        while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        fos.close();
        in.close();
    }
}
