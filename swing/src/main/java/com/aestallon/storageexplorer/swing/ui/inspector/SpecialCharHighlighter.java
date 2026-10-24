package com.aestallon.storageexplorer.swing.ui.inspector;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

/**
 * Tier-1 solution: scans an RSyntaxTextArea's text ONCE (text is assumed
 * immutable after this is called) and installs overlay highlights that mark
 * zero-width / otherwise invisible Unicode characters with a small colored,
 * labeled box.
 *
 * Because these characters typically have zero advance width in most fonts,
 * the marker box is drawn with a fixed pixel width regardless of the
 * character's natural (zero) width. This means the box can slightly overlap
 * the glyph immediately following the special character — an accepted
 * tradeoff for this tier; see Tier 2 if you need reserved layout space
 * instead.
 *
 * Usage:
 *   RSyntaxTextArea textArea = new RSyntaxTextArea();
 *   textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
 *   textArea.setText(someStaticSource);
 *   SpecialCharHighlighter.install(textArea);
 */
public final class SpecialCharHighlighter {

  private SpecialCharHighlighter() {
  }

  /** Label + color to use when painting a marker for a given character. */
  public static final class SpecialCharStyle {
    final String label;
    final Color color;

    public SpecialCharStyle(String label, Color color) {
      this.label = label;
      this.color = color;
    }
  }

  /** Reasonable default set of invisible/format characters worth flagging. */
  public static Map<Character, SpecialCharStyle> defaultStyles() {
    Map<Character, SpecialCharStyle> styles = new HashMap<>();
    styles.put('\u200B', new SpecialCharStyle("ZWSP", new Color(255, 99, 71)));    // zero width space
    styles.put('\uFEFF', new SpecialCharStyle("ZWNBSP", new Color(255, 165, 0)));  // zero width no-break space / BOM
    styles.put('\uFFEF', new SpecialCharStyle("ZWNBSP", new Color(255, 165, 0)));  // zero width no-break space / BOM
    styles.put('\u00A0', new SpecialCharStyle("NBSP", new Color(70, 130, 180)));   // no-break space (usually has real width)
    styles.put('\u00AD', new SpecialCharStyle("SHY", new Color(147, 112, 219)));   // soft hyphen
    styles.put('\u200E', new SpecialCharStyle("LRM", new Color(60, 179, 113)));    // left-to-right mark
    styles.put('\u200F', new SpecialCharStyle("RLM", new Color(220, 20, 60)));     // right-to-left mark
    styles.put('\u2060', new SpecialCharStyle("WJ", new Color(128, 128, 128)));    // word joiner
    return styles;
  }

  /** Installs highlights for the default character set. */
  public static void install(RSyntaxTextArea textArea) {
    install(textArea, defaultStyles());
  }

  /** Installs highlights for a caller-supplied character/style map. */
  public static void install(RSyntaxTextArea textArea, Map<Character, SpecialCharStyle> styles) {
    String text;
    try {
      text = textArea.getDocument().getText(0, textArea.getDocument().getLength());
    } catch (BadLocationException e) {
      // Can't happen: we're reading the full, current length of the document.
      throw new IllegalStateException(e);
    }

    Highlighter highlighter = textArea.getHighlighter();

    for (int i = 0; i < text.length(); i++) {
      SpecialCharStyle style = styles.get(text.charAt(i));
      if (style == null) {
        continue;
      }
      try {
        highlighter.addHighlight(i, i + 1, new SpecialCharPainter(style));
      } catch (BadLocationException e) {
        // Can't happen: offsets come directly from the text we just read.
        throw new IllegalStateException(e);
      }
    }
  }

  /**
   * Paints a small filled, labeled box at the character's position instead
   * of relying on the (usually zero) natural width of the highlighted run.
   */
  private static final class SpecialCharPainter implements Highlighter.HighlightPainter {

    private final SpecialCharStyle style;

    SpecialCharPainter(SpecialCharStyle style) {
      this.style = style;
    }

    @Override
    public void paint(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c) {
      try {
        Rectangle2D r0 = c.modelToView2D(offs0).getBounds2D();

        FontMetrics fm = c.getFontMetrics(c.getFont());
        int boxWidth = Math.max(6, fm.charWidth('n'));
        int x = (int) r0.getX();
        int y = (int) r0.getY();
        int h = (int) Math.ceil(r0.getHeight());

        g.setColor(style.color);
        g.fillRect(x, y, boxWidth, h);

        g.setColor(Color.WHITE);
        Font oldFont = g.getFont();
        float labelSize = Math.max(7f, h * 0.55f);
        g.setFont(oldFont.deriveFont(Font.PLAIN, labelSize));
        // Best-effort label; will often clip for multi-char labels at
        // small box widths, but still gives a visible, colored cue.
        g.drawString(style.label, x + 1, y + h - 2);
        g.setFont(oldFont);
      } catch (BadLocationException e) {
        // Offset became invalid; shouldn't happen given the immutable-text assumption.
      }
    }
  }
}
