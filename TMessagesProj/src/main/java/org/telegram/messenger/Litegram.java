package org.telegram.messenger;

/**
 * Litegram — modo ultra-lite.
 * Flags static final: o javac remove os ramos desativados.
 * Envios permitidos: texto, galeria (foto), música/áudio, documentos.
 * Sem: chamadas VoIP, stories/vídeo redondo, bots, inline de bots.
 */
public final class Litegram {
    public static final boolean ENABLE_CALLS = false;
    public static final boolean ENABLE_STORIES = false;
    public static final boolean ENABLE_ROUND_VIDEO = false;
    public static final boolean ENABLE_BOTS = false;
    public static final boolean ENABLE_BOT_INLINE = false;
    public static final boolean ENABLE_BOT_KEYBOARD = false;

    private Litegram() {}

    public static boolean isBot(TLRPC.User user) {
        return user != null && user.bot;
    }

    /** Esconde bots da lista de pessoas: só gente de verdade. */
    public static boolean hideBot(TLRPC.User user) {
        return !ENABLE_BOTS && isBot(user);
    }
}
