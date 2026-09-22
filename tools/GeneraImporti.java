import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Genera i PNG per la demo VisioVoice — Assegno Unico 2026.
 *
 * Eseguire dalla radice del progetto:
 *   java tools/GeneraImporti.java
 *
 * Output:
 *   src/main/resources/static/demo/importi-2026.png
 *   src/main/resources/static/demo/step-indicator-1.png  (... fino a 5)
 *
 * IMPORTANTE: i valori nella tabella devono coincidere carattere per carattere
 * con i sourceFacts nel file scenarios/inps-assegno-unico.json.
 *
 * Fonte dati: INPS Circolare n. 7 del 30 gennaio 2026.
 * Valori intermedi (165,90 e 115,90) da informazionescuola.it (cita Circ. 7/2026).
 */
public class GeneraImporti {

    static final Color INPS_BLUE      = new Color(0,   61, 125);
    static final Color INPS_BLUE_MID  = new Color(0,   85, 165);
    static final Color ROW_STRIPE     = new Color(232, 240, 249);
    static final Color BORDER         = new Color(180, 205, 235);
    static final Color TEXT_DARK      = new Color( 25,  25,  25);
    static final Color TEXT_LIGHT     = Color.WHITE;
    static final Color TEXT_FOOT      = new Color( 90,  90,  90);

    // Le righe della tabella — testo esatto come in sourceFacts
    static final String[][] ROWS = {
        { "fino a 17.468,51 €",                   "203,80 €" },
        { "da 17.468,52 € a 25.000,00 €",   "165,90 €" },
        { "da 25.001,00 € a 35.000,00 €",   "115,90 €" },
        { "oltre 46.582,71 € o senza ISEE",       " 58,30 €" }
    };

    static final int IMG_W   = 820;
    static final int MARGIN  = 22;
    static final int HDR_H   = 46;
    static final int ROW_H   = 40;
    static final int COL2_X  = 560;   // x inizio colonna importo

    public static void main(String[] args) throws IOException {
        File outDir = new File("src/main/resources/static/demo");
        outDir.mkdirs();

        generateTable(outDir);
        for (int step = 1; step <= 5; step++) {
            generateStepIndicator(outDir, step, 5);
        }
        System.out.println("OK — tutti i PNG generati in: " + outDir.getAbsolutePath());
    }

    // -----------------------------------------------------------------------
    //  Tabella importi
    // -----------------------------------------------------------------------
    static void generateTable(File dir) throws IOException {
        int titleArea = 50;
        int tableH    = HDR_H + ROWS.length * ROW_H;
        int footArea  = 38;
        int imgH      = MARGIN + titleArea + tableH + footArea + MARGIN;

        BufferedImage img = new BufferedImage(IMG_W, imgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = setup(img);

        // Sfondo bianco
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, IMG_W, imgH);

        // Titolo
        g.setColor(INPS_BLUE);
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.drawString(
            "Importi mensili Assegno Unico Universale 2026 — figli minorenni",
            MARGIN, MARGIN + 28);

        // Intestazione tabella
        int hdrY = MARGIN + titleArea;
        g.setColor(INPS_BLUE);
        g.fillRect(MARGIN, hdrY, IMG_W - 2 * MARGIN, HDR_H);
        g.setColor(TEXT_LIGHT);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("Fascia ISEE",                COL2_X - 350 + 10, hdrY + 30);
        g.drawString("Importo mensile per figlio", COL2_X + 14,        hdrY + 30);

        // Separatore verticale intestazione
        g.setColor(new Color(140, 175, 215));
        g.fillRect(COL2_X, hdrY, 2, HDR_H);

        // Righe dati
        for (int i = 0; i < ROWS.length; i++) {
            int rowY = hdrY + HDR_H + i * ROW_H;
            g.setColor(i % 2 == 0 ? ROW_STRIPE : Color.WHITE);
            g.fillRect(MARGIN, rowY, IMG_W - 2 * MARGIN, ROW_H);

            // Separatore verticale
            g.setColor(BORDER);
            g.fillRect(COL2_X, rowY, 1, ROW_H);
            // Separatore orizzontale
            g.fillRect(MARGIN, rowY + ROW_H - 1, IMG_W - 2 * MARGIN, 1);

            // Testo fascia ISEE
            g.setColor(TEXT_DARK);
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.drawString(ROWS[i][0], MARGIN + 12, rowY + 26);

            // Testo importo (grassetto, allineato a sinistra nella colonna)
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.drawString(ROWS[i][1], COL2_X + 14, rowY + 26);
        }

        // Bordo esterno tabella
        g.setColor(INPS_BLUE);
        g.drawRect(MARGIN, hdrY, IMG_W - 2 * MARGIN, HDR_H + ROWS.length * ROW_H);

        // Nota a pie' di tabella
        int footY = hdrY + HDR_H + ROWS.length * ROW_H + 22;
        g.setColor(TEXT_FOOT);
        g.setFont(new Font("SansSerif", Font.ITALIC, 11));
        g.drawString(
            "Fonte: INPS Circolare n. 7 del 30 gennaio 2026, Allegato 1 — valori intermedi da informazionescuola.it",
            MARGIN, footY);

        g.dispose();
        File out = new File(dir, "importi-2026.png");
        ImageIO.write(img, "PNG", out);
        System.out.println("  -> " + out.getName() + " (" + IMG_W + "x" + imgH + ")");
    }

    // -----------------------------------------------------------------------
    //  Indicatori di step (step-indicator-N.png, N=1..5)
    // -----------------------------------------------------------------------
    static void generateStepIndicator(File dir, int current, int total) throws IOException {
        String[] labels = { "Richiedente", "Figli", "ISEE", "Banca", "Invio" };
        int cD      = 30;    // diametro cerchio
        int spacing = 110;   // distanza tra centri
        int imgW    = total * spacing + 40;
        int imgH    = 80;
        int cy      = 34;    // centro y cerchi

        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = setup(img);

        g.setColor(new Color(246, 246, 246));
        g.fillRect(0, 0, imgW, imgH);

        for (int i = 0; i < total; i++) {
            int cx    = 20 + i * spacing + cD / 2;
            boolean active = (i + 1 == current);
            boolean done   = (i + 1 < current);

            // Linea connettore
            if (i < total - 1) {
                g.setColor(done ? INPS_BLUE_MID : new Color(200, 200, 200));
                g.fillRect(cx + cD / 2, cy - 2, spacing - cD, 4);
            }

            // Cerchio
            Color fillC = (active || done) ? INPS_BLUE : new Color(200, 200, 200);
            g.setColor(fillC);
            g.fillOval(cx - cD / 2, cy - cD / 2, cD, cD);

            // Numero nel cerchio
            g.setColor(TEXT_LIGHT);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            FontMetrics fm = g.getFontMetrics();
            String num = String.valueOf(i + 1);
            int numX = cx - fm.stringWidth(num) / 2;
            int numY = cy + fm.getAscent() / 2 - 2;
            g.drawString(num, numX, numY);

            // Etichetta sotto il cerchio
            g.setColor(active ? INPS_BLUE : new Color(80, 80, 80));
            g.setFont(new Font("SansSerif", active ? Font.BOLD : Font.PLAIN, 10));
            fm = g.getFontMetrics();
            String lbl = labels[i];
            int lblX = cx - fm.stringWidth(lbl) / 2;
            g.drawString(lbl, lblX, cy + cD / 2 + 16);
        }

        g.dispose();
        File out = new File(dir, "step-indicator-" + current + ".png");
        ImageIO.write(img, "PNG", out);
        System.out.println("  -> " + out.getName() + " (" + imgW + "x" + imgH + ")");
    }

    // -----------------------------------------------------------------------
    //  Utility: crea Graphics2D con antialiasing attivo
    // -----------------------------------------------------------------------
    static Graphics2D setup(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }
}
