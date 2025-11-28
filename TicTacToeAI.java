/*
 * TicTacToeAI.java
 *
 * A single-file Java Swing Tic-Tac-Toe game with an AI opponent using minimax
 * with alpha-beta pruning, move ordering and a polished UI.
 *
 * Compile and run:
 *   javac TicTacToeAI.java
 *   java TicTacToeAI
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.*;
import java.util.List;

public class TicTacToeAI {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameFrame().setVisible(true));
    }
}

/* --------- MODEL: Board (0-empty, 1-human X, -1-ai O) --------- */
class Board {
    private int[] cells = new int[9];
    private Deque<Integer> history = new ArrayDeque<>();

    public Board() { clear(); }

    public void clear() { Arrays.fill(cells, 0); history.clear(); }

    public boolean makeMove(int idx, int player) {
        if (idx < 0 || idx >= 9 || cells[idx] != 0) return false;
        cells[idx] = player; history.push(idx); return true;
    }

    public boolean undo() {
        if (history.isEmpty()) return false;
        int last = history.pop(); cells[last] = 0; return true;
    }

    public int get(int idx) { return cells[idx]; }

    public int[] getCellsCopy() { return Arrays.copyOf(cells, 9); }

    public List<Integer> emptyIndices() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (cells[i] == 0) list.add(i);
        return list;
    }

    // Return 1 if X wins, -1 if O wins, 0 otherwise (no winner yet), 2 if draw
    public int checkWinner() {
        int[][] lines = { {0,1,2},{3,4,5},{6,7,8}, {0,3,6},{1,4,7},{2,5,8}, {0,4,8},{2,4,6} };
        for (int[] l : lines) {
            int s = cells[l[0]] + cells[l[1]] + cells[l[2]];
            if (s == 3) return 1; // X
            if (s == -3) return -1; // O
        }
        for (int v : cells) if (v == 0) return 0; // game continues
        return 2; // draw
    }

    public boolean isFull() { for (int v : cells) if (v == 0) return false; return true; }

    public String toString() { return Arrays.toString(cells); }
}

/* --------- AI: Minimax with alpha-beta and move ordering --------- */
class AIPlayer {
    private final int aiPlayer; // -1 for O
    private final int humanPlayer; // +1 for X
    private int maxDepth = 9; // can be reduced for difficulty

    public AIPlayer(int aiPlayer) {
        this.aiPlayer = aiPlayer;
        this.humanPlayer = -aiPlayer;
    }

    public void setMaxDepth(int d) { maxDepth = Math.max(1, Math.min(9, d)); }

    // Public API: pick best move (index)
    public int pickMove(Board board) {
        int bestScore = Integer.MIN_VALUE;
        int bestMove = -1;
        // generate moves with ordering (center -> corners -> edges)
        List<Integer> moves = orderedMoves(board);
        for (int m : moves) {
            board.makeMove(m, aiPlayer);
            int score = -negamax(board, maxDepth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1, -aiPlayer);
            board.undo();
            if (score > bestScore) { bestScore = score; bestMove = m; }
        }
        return bestMove;
    }

    // Negamax wrapper using evaluation: win=1000, draw=0, loss=-1000
    private int negamax(Board board, int depth, int alpha, int beta, int currentPlayer) {
        int winner = board.checkWinner();
        if (winner == aiPlayer) return 1000 + depth; // prefer faster wins
        if (winner == humanPlayer) return -1000 - depth; // avoid slower losses
        if (winner == 2) return 0;
        if (depth == 0) return heuristic(board);

        int max = Integer.MIN_VALUE / 4;
        List<Integer> moves = orderedMoves(board);
        for (int m : moves) {
            board.makeMove(m, currentPlayer);
            int val = -negamax(board, depth - 1, -beta, -alpha, -currentPlayer);
            board.undo();
            if (val > max) max = val;
            alpha = Math.max(alpha, val);
            if (alpha >= beta) break; // alpha-beta cutoff
        }
        return max;
    }

    // Simple heuristic: favor center, corners, block/threat detection
    private int heuristic(Board board) {
        int score = 0;
        int[] b = board.getCellsCopy();
        // center
        if (b[4] == aiPlayer) score += 3;
        else if (b[4] == humanPlayer) score -= 3;
        // corners
        int[] corners = {0,2,6,8};
        for (int c : corners) { if (b[c] == aiPlayer) score += 2; else if (b[c] == humanPlayer) score -= 2; }
        // lines opportunity: each line with ai pieces and empty -> +
        int[][] lines = { {0,1,2},{3,4,5},{6,7,8}, {0,3,6},{1,4,7},{2,5,8}, {0,4,8},{2,4,6} };
        for (int[] l : lines) {
            int s = b[l[0]] + b[l[1]] + b[l[2]];
            if (s == 2*aiPlayer) score += 40; // immediate win threat
            if (s == 2*humanPlayer) score -= 35; // need to block
        }
        return score;
    }

    // Move ordering: center first, then corners, then edges
    private List<Integer> orderedMoves(Board board) {
        List<Integer> empties = board.emptyIndices();
        empties.sort((a,b) -> Integer.compare(movePriority(b), movePriority(a)));
        return empties;
    }

    private int movePriority(int idx) {
        if (idx == 4) return 100;
        if (idx == 0 || idx == 2 || idx == 6 || idx == 8) return 50;
        return 10; // edges
    }
}

/* --------- VIEW: GameFrame and BoardPanel --------- */
class GameFrame extends JFrame {
    private final Board board = new Board();
    private final AIPlayer ai = new AIPlayer(-1);
    private boolean playerTurn = true; // human (X) starts
    private BoardPanel boardPanel;
    private final JLabel statusLabel = new JLabel("Your turn — X");
    private final JSlider difficultySlider = new JSlider(1, 9, 9);
    private final JComboBox<String> themeBox;

    public GameFrame() {
        setTitle("Tic-Tac-Toe — Calm & Cozy");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(560, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Top control bar
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 16f));
        top.add(statusLabel, BorderLayout.WEST);

        JPanel controls = new JPanel();
        JButton undoBtn = new JButton("Undo");
        JButton restartBtn = new JButton("Restart");
        undoBtn.addActionListener(e -> { if (board.undo()) { playerTurn = !playerTurn; boardPanel.repaint(); updateStatus(); } });
        restartBtn.addActionListener(e -> { board.clear(); playerTurn = true; updateStatus(); boardPanel.repaint(); });
        controls.add(undoBtn); controls.add(restartBtn);

        top.add(controls, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // Board panel center
        boardPanel = new BoardPanel(board, (idx) -> humanMove(idx));
        add(boardPanel, BorderLayout.CENTER);

        // Bottom: settings
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(BorderFactory.createEmptyBorder(8,12,12,12));

        JPanel diff = new JPanel(new FlowLayout(FlowLayout.LEFT));
        diff.add(new JLabel("Difficulty: "));
        difficultySlider.setMajorTickSpacing(2); difficultySlider.setPaintTicks(true); difficultySlider.setPaintLabels(true);
        difficultySlider.addChangeListener(e -> { ai.setMaxDepth(difficultySlider.getValue()); });
        diff.add(difficultySlider);
        bottom.add(diff);

        JPanel themeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        themeRow.add(new JLabel("Theme: "));
        themeBox = new JComboBox<>(new String[]{"Light","Dark","Calm"});
        themeBox.addActionListener(e -> applyTheme((String)themeBox.getSelectedItem()));
        themeRow.add(themeBox);
        bottom.add(themeRow);

        add(bottom, BorderLayout.SOUTH);

        // set defaults
        ai.setMaxDepth(9);
        applyTheme("Calm");
    }

    private void applyTheme(String t) {
        if (t.equals("Light")) boardPanel.setTheme(new Theme(new Color(245,245,245), new Color(30,30,30), new Color(200,230,255), new Color(210,220,230)));
        else if (t.equals("Dark")) boardPanel.setTheme(new Theme(new Color(20,20,20), new Color(220,220,220), new Color(60,60,80), new Color(80,80,100)));
        else boardPanel.setTheme(new Theme(new Color(247,241,237), new Color(38,50,56), new Color(224,242,241), new Color(210,234,225)));
        boardPanel.repaint();
    }

    private void humanMove(int idx) {
        if (!playerTurn) return; // ignore if AI is thinking
        if (!board.makeMove(idx, 1)) return; // invalid
        playerTurn = false;
        boardPanel.repaint();
        int winner = board.checkWinner();
        if (winner != 0) { endGame(winner); return; }
        // AI move (run on Swing thread but small; for deeper depth move to background if desired)
        SwingUtilities.invokeLater(() -> {
            int aiMove = ai.pickMove(board);
            if (aiMove >= 0) board.makeMove(aiMove, -1);
            playerTurn = true;
            boardPanel.repaint();
            int w = board.checkWinner();
            if (w != 0) endGame(w);
        });
    }

    private void endGame(int winner) {
        if (winner == 1) statusLabel.setText("You win! 🎉");
        else if (winner == -1) statusLabel.setText("AI wins — better luck next time.");
        else statusLabel.setText("Draw — a calm stalemate.");
    }

    private void updateStatus() {
        int w = board.checkWinner();
        if (w == 1) statusLabel.setText("You win! 🎉");
        else if (w == -1) statusLabel.setText("AI wins — better luck next time.");
        else if (w == 2) statusLabel.setText("Draw — a calm stalemate.");
        else statusLabel.setText(playerTurn ? "Your turn — X" : "AI thinking...");
    }
}

/* Theme container */
class Theme {
    public final Color background; public final Color foreground; public final Color cell; public final Color accent;
    public Theme(Color bg, Color fg, Color cell, Color accent) { this.background = bg; this.foreground = fg; this.cell = cell; this.accent = accent; }
}

/* BoardPanel: custom painting + mouse handling + animations */
class BoardPanel extends JPanel {
    private final Board board;
    private ConsumerInt onCellClick;
    private Theme theme = new Theme(Color.WHITE, Color.BLACK, new Color(220,220,220), new Color(100,180,220));
    private int hoverIndex = -1;
    private int pressIndex = -1;

    public BoardPanel(Board b, ConsumerInt onCellClick) {
        this.board = b; this.onCellClick = onCellClick;
        setPreferredSize(new Dimension(560,560));
        setBackground(theme.background);
        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) { hoverIndex = locateCell(e.getX(), e.getY()); repaint(); }
        });
        addMouseListener(new MouseAdapter() {
            public void mouseExited(MouseEvent e) { hoverIndex = -1; repaint(); }
            public void mousePressed(MouseEvent e) { pressIndex = locateCell(e.getX(), e.getY()); repaint(); }
            public void mouseReleased(MouseEvent e) { int idx = locateCell(e.getX(), e.getY()); pressIndex = -1; repaint(); if (idx >= 0) onCellClick.accept(idx); }
        });
    }

    public void setTheme(Theme t) { this.theme = t; setBackground(t.background); }

    private int locateCell(int x, int y) {
        int size = Math.min(getWidth(), getHeight());
        int margin = size / 12;
        int boardSize = size - margin*2;
        int cellSize = boardSize/3;
        int bx = (getWidth()-boardSize)/2; int by = (getHeight()-boardSize)/2;
        if (x < bx || x > bx+boardSize || y < by || y > by+boardSize) return -1;
        int col = (x - bx) / cellSize; int row = (y - by) / cellSize; return row*3 + col;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int size = Math.min(getWidth(), getHeight());
        int margin = size / 12;
        int boardSize = size - margin*2;
        int cellSize = boardSize/3;
        int bx = (getWidth()-boardSize)/2; int by = (getHeight()-boardSize)/2;

        // board background
        g.setColor(theme.cell);
        g.fillRoundRect(bx-6, by-6, boardSize+12, boardSize+12, 24, 24);

        // grid lines
        g.setStroke(new BasicStroke(Math.max(4, boardSize/80f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(theme.foreground);
        for (int i = 1; i <= 2; i++) {
            int x = bx + i*cellSize;
            g.drawLine(x, by+8, x, by+boardSize-8);
            int y = by + i*cellSize;
            g.drawLine(bx+8, y, bx+boardSize-8, y);
        }

        // cells
        for (int i = 0; i < 9; i++) {
            int r = i / 3, c = i % 3;
            int cx = bx + c*cellSize, cy = by + r*cellSize;
            // hover
            if (i == hoverIndex) {
                g.setColor(theme.accent);
                Composite old = g.getComposite(); g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f));
                g.fillRoundRect(cx+6, cy+6, cellSize-12, cellSize-12, 12, 12);
                g.setComposite(old);
            }
            // press ripple
            if (i == pressIndex) {
                g.setColor(theme.accent.darker());
                Composite old = g.getComposite(); g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.16f));
                g.fillOval(cx + cellSize/4, cy + cellSize/4, cellSize/2, cellSize/2);
                g.setComposite(old);
            }

            int val = board.get(i);
            if (val == 1) drawX(g, cx, cy, cellSize);
            else if (val == -1) drawO(g, cx, cy, cellSize);
        }

        g.dispose();
    }

    private void drawX(Graphics2D g, int x, int y, int s) {
        int pad = Math.max(18, s/8);
        g.setStroke(new BasicStroke(Math.max(6, s/20), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(theme.foreground);
        g.drawLine(x+pad, y+pad, x+s-pad, y+s-pad);
        g.drawLine(x+pad, y+s-pad, x+s-pad, y+pad);
    }

    private void drawO(Graphics2D g, int x, int y, int s) {
        int pad = Math.max(16, s/10);
        g.setStroke(new BasicStroke(Math.max(6, s/20), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(theme.foreground);
        g.drawOval(x+pad, y+pad, s-2*pad, s-2*pad);
    }
}

/* Simple integer consumer for callbacks */
interface ConsumerInt { void accept(int v); }
