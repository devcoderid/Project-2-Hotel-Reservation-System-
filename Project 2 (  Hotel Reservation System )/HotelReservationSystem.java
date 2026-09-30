import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.table.*;

public class HotelReservationSystem {

    static final String HOTEL = "Grand Horizon Hotel";
    static final double GST = 0.12;
    static final String FONT = "Segoe UI";
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    static final Color NAVY = new Color(0x12355B);
    static final Color NAVY_LIGHT = new Color(0x1F5A8E);
    static final Color GOLD = new Color(0xC9A227);
    static final Color GOLD_DARK = new Color(0xA8871A);
    static final Color BG = new Color(0xF5F2EB);
    static final Color TEXT = new Color(0x1F2933);
    static final Color MUTED = new Color(0x7A8494);
    static final Color LINE = new Color(0xDDD6C8);
    static final Color SELECT = new Color(0xE3ECF6);
    static final Color GREEN = new Color(0x1E8E5A);
    static final Color RED = new Color(0xC0392B);

    static String money(double v) {
        return "Rs. " + String.format(Locale.US, "%,.2f", v);
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    static String clean(String s) {
        return s == null ? "" : s.replace("|", " ").replace("\n", " ").replace("\r", " ").trim();
    }

    enum RoomType {
        STANDARD("Standard", 2500, 2, "Queen bed, Wi-Fi, TV, Air conditioning"),
        DELUXE("Deluxe", 4500, 3, "King bed, City view, Mini bar, Smart TV, Wi-Fi"),
        SUITE("Suite", 8000, 4, "Lounge area, King bed, Jacuzzi, Free breakfast, Butler service");

        final String label;
        final double price;
        final int capacity;
        final String amenities;

        RoomType(String label, double price, int capacity, String amenities) {
            this.label = label;
            this.price = price;
            this.capacity = capacity;
            this.amenities = amenities;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum Status { CONFIRMED, CANCELLED }

    static class Room {
        final int number;
        final RoomType type;

        Room(int number, RoomType type) {
            this.number = number;
            this.type = type;
        }

        int floor() {
            return number / 100;
        }
    }

    static class Quote {
        final long nights;
        final double roomCharge;
        final double tax;
        final double total;

        Quote(long nights, double roomCharge, double tax, double total) {
            this.nights = nights;
            this.roomCharge = roomCharge;
            this.tax = tax;
            this.total = total;
        }
    }

    static class Booking {
        String id, guestName, phone, email, paymentMethod, transactionId;
        int roomNumber, guests;
        RoomType type;
        LocalDate checkIn, checkOut;
        double roomCharge, tax, total, refund;
        Status status;
        LocalDateTime bookedOn;

        long nights() {
            return ChronoUnit.DAYS.between(checkIn, checkOut);
        }

        String serialize() {
            return String.join("|", id, clean(guestName), clean(phone), clean(email),
                    String.valueOf(roomNumber), type.name(), checkIn.toString(), checkOut.toString(),
                    String.valueOf(guests), String.format(Locale.US, "%.2f", roomCharge),
                    String.format(Locale.US, "%.2f", tax), String.format(Locale.US, "%.2f", total),
                    clean(paymentMethod), clean(transactionId), status.name(), bookedOn.toString(),
                    String.format(Locale.US, "%.2f", refund));
        }

        static Booking parse(String line) {
            String[] p = line.split("\\|", -1);
            Booking b = new Booking();
            b.id = p[0];
            b.guestName = p[1];
            b.phone = p[2];
            b.email = p[3];
            b.roomNumber = Integer.parseInt(p[4]);
            b.type = RoomType.valueOf(p[5]);
            b.checkIn = LocalDate.parse(p[6]);
            b.checkOut = LocalDate.parse(p[7]);
            b.guests = Integer.parseInt(p[8]);
            b.roomCharge = Double.parseDouble(p[9]);
            b.tax = Double.parseDouble(p[10]);
            b.total = Double.parseDouble(p[11]);
            b.paymentMethod = p[12];
            b.transactionId = p[13];
            b.status = Status.valueOf(p[14]);
            b.bookedOn = LocalDateTime.parse(p[15]);
            b.refund = Double.parseDouble(p[16]);
            return b;
        }
    }

    static class HotelDatabase {
        private final Path dir = Paths.get("hotel_data");
        private final Path roomsFile = dir.resolve("rooms.txt");
        private final Path bookingsFile = dir.resolve("bookings.txt");

        HotelDatabase() {
            try {
                Files.createDirectories(dir);
                if (!Files.exists(roomsFile)) {
                    writeDefaultRooms();
                }
                if (!Files.exists(bookingsFile)) {
                    Files.write(bookingsFile, new byte[0]);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Unable to prepare data files: " + e.getMessage(), e);
            }
        }

        private void writeDefaultRooms() throws IOException {
            List<String> lines = new ArrayList<>();
            for (int i = 1; i <= 6; i++) lines.add((100 + i) + "," + RoomType.STANDARD.name());
            for (int i = 1; i <= 4; i++) lines.add((200 + i) + "," + RoomType.DELUXE.name());
            for (int i = 1; i <= 2; i++) lines.add((300 + i) + "," + RoomType.SUITE.name());
            Files.write(roomsFile, lines, StandardCharsets.UTF_8);
        }

        List<Room> loadRooms() {
            List<Room> rooms = new ArrayList<>();
            try {
                for (String line : Files.readAllLines(roomsFile, StandardCharsets.UTF_8)) {
                    if (line.trim().isEmpty()) continue;
                    String[] p = line.split(",");
                    rooms.add(new Room(Integer.parseInt(p[0].trim()), RoomType.valueOf(p[1].trim())));
                }
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("Unable to read rooms file: " + e.getMessage(), e);
            }
            rooms.sort(Comparator.comparingInt(r -> r.number));
            return rooms;
        }

        List<Booking> loadBookings() {
            List<Booking> list = new ArrayList<>();
            try {
                for (String line : Files.readAllLines(bookingsFile, StandardCharsets.UTF_8)) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        list.add(Booking.parse(line));
                    } catch (RuntimeException ignored) {
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read bookings file: " + e.getMessage(), e);
            }
            return list;
        }

        void saveBookings(List<Booking> list) {
            List<String> lines = new ArrayList<>();
            for (Booking b : list) lines.add(b.serialize());
            try {
                Files.write(bookingsFile, lines, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to save bookings: " + e.getMessage(), e);
            }
        }
    }

    static class HotelService {
        private final HotelDatabase db = new HotelDatabase();
        private final List<Room> rooms;
        private final List<Booking> bookings;

        HotelService() {
            rooms = db.loadRooms();
            bookings = db.loadBookings();
        }

        List<Room> rooms() {
            return Collections.unmodifiableList(rooms);
        }

        synchronized List<Booking> bookings() {
            List<Booking> copy = new ArrayList<>(bookings);
            copy.sort(Comparator.comparing((Booking b) -> b.bookedOn).reversed());
            return copy;
        }

        synchronized int activeBookings() {
            int n = 0;
            for (Booking b : bookings) if (b.status == Status.CONFIRMED) n++;
            return n;
        }

        synchronized boolean isFree(int roomNumber, LocalDate in, LocalDate out) {
            for (Booking b : bookings) {
                if (b.roomNumber == roomNumber && b.status == Status.CONFIRMED
                        && b.checkIn.isBefore(out) && in.isBefore(b.checkOut)) {
                    return false;
                }
            }
            return true;
        }

        List<Room> search(LocalDate in, LocalDate out, int guests, RoomType type) {
            List<Room> found = new ArrayList<>();
            for (Room r : rooms) {
                if (type != null && r.type != type) continue;
                if (r.type.capacity < guests) continue;
                if (isFree(r.number, in, out)) found.add(r);
            }
            return found;
        }

        static Quote quote(RoomType type, LocalDate in, LocalDate out) {
            long nights = ChronoUnit.DAYS.between(in, out);
            double room = round2(type.price * nights);
            double tax = round2(room * GST);
            return new Quote(nights, room, tax, round2(room + tax));
        }

        synchronized Booking book(String name, String phone, String email, Room room, LocalDate in, LocalDate out,
                                  int guests, String method, String transactionId) {
            if (!isFree(room.number, in, out)) {
                throw new IllegalStateException("Sorry, room " + room.number + " has just been booked for these dates.");
            }
            Quote q = quote(room.type, in, out);
            Booking b = new Booking();
            b.id = nextId();
            b.guestName = clean(name);
            b.phone = clean(phone);
            b.email = clean(email);
            b.roomNumber = room.number;
            b.type = room.type;
            b.checkIn = in;
            b.checkOut = out;
            b.guests = guests;
            b.roomCharge = q.roomCharge;
            b.tax = q.tax;
            b.total = q.total;
            b.paymentMethod = method;
            b.transactionId = transactionId;
            b.status = Status.CONFIRMED;
            b.bookedOn = LocalDateTime.now().withNano(0);
            b.refund = 0;
            bookings.add(b);
            db.saveBookings(bookings);
            return b;
        }

        boolean canCancel(Booking b) {
            return b.status == Status.CONFIRMED && LocalDate.now().isBefore(b.checkOut);
        }

        double refundFor(Booking b) {
            if ("Pay at Hotel".equals(b.paymentMethod)) return 0;
            long days = ChronoUnit.DAYS.between(LocalDate.now(), b.checkIn);
            if (days >= 2) return b.total;
            if (days == 1) return round2(b.total * 0.5);
            return 0;
        }

        synchronized double cancel(String id) {
            Booking b = find(id);
            if (b == null) throw new IllegalStateException("Booking not found.");
            if (!canCancel(b)) throw new IllegalStateException("This booking can no longer be cancelled.");
            double refund = refundFor(b);
            b.status = Status.CANCELLED;
            b.refund = refund;
            db.saveBookings(bookings);
            return refund;
        }

        synchronized Booking find(String id) {
            for (Booking b : bookings) if (b.id.equalsIgnoreCase(id)) return b;
            return null;
        }

        String todayStatus(Room room) {
            LocalDate today = LocalDate.now();
            return isFree(room.number, today, today.plusDays(1)) ? "Available" : "Occupied";
        }

        private String nextId() {
            int max = 0;
            for (Booking b : bookings) {
                try {
                    max = Math.max(max, Integer.parseInt(b.id.substring(2)));
                } catch (RuntimeException ignored) {
                }
            }
            return String.format("BK%05d", max + 1);
        }
    }

    static class PaymentResult {
        final boolean success;
        final String method;
        final String transactionId;
        final String message;

        PaymentResult(boolean success, String method, String transactionId, String message) {
            this.success = success;
            this.method = method;
            this.transactionId = transactionId;
            this.message = message;
        }
    }

    static class PaymentGateway {
        static final Pattern UPI = Pattern.compile("^[\\w.\\-]{2,}@[A-Za-z]{2,}$");
        static final Random RANDOM = new Random();

        static String validate(String method, Map<String, String> d) {
            switch (method) {
                case "Credit / Debit Card": {
                    String number = d.get("number").replaceAll("\\s", "");
                    if (!number.matches("\\d{16}")) return "Card number must contain 16 digits.";
                    if (d.get("name").trim().isEmpty()) return "Please enter the name on the card.";
                    String exp = d.get("expiry").trim();
                    if (!exp.matches("(0[1-9]|1[0-2])/\\d{2}")) return "Expiry must be in MM/YY format.";
                    YearMonth ym = YearMonth.of(2000 + Integer.parseInt(exp.substring(3)), Integer.parseInt(exp.substring(0, 2)));
                    if (ym.isBefore(YearMonth.now())) return "This card has expired.";
                    if (!d.get("cvv").trim().matches("\\d{3}")) return "CVV must be 3 digits.";
                    return null;
                }
                case "UPI":
                    return UPI.matcher(d.get("upi").trim()).matches() ? null : "Enter a valid UPI ID (example: name@bank).";
                case "Net Banking":
                    return d.get("bank").isEmpty() ? "Please choose your bank." : null;
                default:
                    return null;
            }
        }

        static PaymentResult charge(String method, Map<String, String> d, double amount) {
            String err = validate(method, d);
            if (err != null) return new PaymentResult(false, method, "", err);
            if ("Pay at Hotel".equals(method)) {
                return new PaymentResult(true, method, "PAY-AT-HOTEL", "Payment will be collected at check-in.");
            }
            if ("Credit / Debit Card".equals(method) && d.get("number").replaceAll("\\s", "").endsWith("0000")) {
                return new PaymentResult(false, method, "", "Card declined by the bank (simulated). Please try another card.");
            }
            String txn = "TXN" + (System.currentTimeMillis() % 100000000L) + (100 + RANDOM.nextInt(900));
            return new PaymentResult(true, method, txn, "Payment of " + money(amount) + " successful.");
        }
    }

    static void smooth(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font(FONT, style, size));
        l.setForeground(color);
        return l;
    }

    static JPanel labeled(String caption, JComponent c) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.add(label(caption, Font.BOLD, 12, MUTED), BorderLayout.NORTH);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    static <T extends JComponent> T sized(T c, int w, int h) {
        c.setPreferredSize(new Dimension(w, h));
        return c;
    }

    static JSpinner dateSpinner(LocalDate d, LocalDate min) {
        Date value = Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date start = Date.from(min.atStartOfDay(ZoneId.systemDefault()).toInstant());
        JSpinner s = new JSpinner(new SpinnerDateModel(value, start, null, Calendar.DAY_OF_MONTH));
        s.setEditor(new JSpinner.DateEditor(s, "dd MMM yyyy"));
        s.setFont(new Font(FONT, Font.PLAIN, 14));
        s.setPreferredSize(new Dimension(150, 36));
        return s;
    }

    static LocalDate toLocal(JSpinner s) {
        return ((Date) s.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    static void setDate(JSpinner s, LocalDate d) {
        s.setValue(Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }

    static void styleCombo(JComboBox<?> c) {
        c.setFont(new Font(FONT, Font.PLAIN, 14));
        c.setBackground(Color.WHITE);
        c.setPreferredSize(new Dimension(c.getPreferredSize().width, 36));
    }

    static class BodyRenderer extends DefaultTableCellRenderer {
        private final boolean colorStatus;

        BodyRenderer(boolean colorStatus) {
            this.colorStatus = colorStatus;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, false, r, c);
            l.setBorder(new EmptyBorder(0, 10, 0, 10));
            l.setFont(new Font(FONT, colorStatus ? Font.BOLD : Font.PLAIN, 13));
            if (!sel) {
                l.setBackground(r % 2 == 0 ? Color.WHITE : new Color(0xFAF7F0));
            }
            l.setForeground(TEXT);
            if (colorStatus && v != null) {
                String s = v.toString();
                if (s.equals("CONFIRMED") || s.equals("Available")) l.setForeground(GREEN);
                if (s.equals("CANCELLED") || s.equals("Occupied")) l.setForeground(RED);
            }
            return l;
        }
    }

    static void styleTable(JTable t, int statusColumn, int... widths) {
        t.setRowHeight(34);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setSelectionBackground(SELECT);
        t.setSelectionForeground(TEXT);
        t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setDefaultRenderer(Object.class, new BodyRenderer(false));
        JTableHeader header = t.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(0, 38));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, v, false, false, r, c);
                l.setOpaque(true);
                l.setBackground(NAVY);
                l.setForeground(Color.WHITE);
                l.setFont(new Font(FONT, Font.BOLD, 13));
                l.setBorder(new EmptyBorder(0, 10, 0, 10));
                return l;
            }
        });
        for (int i = 0; i < widths.length && i < t.getColumnCount(); i++) {
            t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        if (statusColumn >= 0) {
            t.getColumnModel().getColumn(statusColumn).setCellRenderer(new BodyRenderer(true));
        }
    }

    static JScrollPane tableScroll(JTable t) {
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(new LineBorder(LINE));
        sp.getViewport().setBackground(Color.WHITE);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    static class GradientPanel extends JPanel {
        GradientPanel() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setPaint(new GradientPaint(0, 0, NAVY, getWidth(), 0, NAVY_LIGHT));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(GOLD);
            g2.fillRect(0, getHeight() - 3, getWidth(), 3);
            g2.dispose();
        }
    }

    static class RoundButton extends JButton {
        private final Color base, hover, outline;
        private final int arc;
        private boolean over;

        RoundButton(String text, Color base, Color hover, Color fg, int arc, Color outline) {
            super(text);
            this.base = base;
            this.hover = hover;
            this.arc = arc;
            this.outline = outline;
            setForeground(fg);
            setFont(new Font(FONT, Font.BOLD, 13));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(new EmptyBorder(9, 22, 9, 22));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    over = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    over = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(!isEnabled() ? new Color(0xC3CAD4) : over ? hover : base);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            if (outline != null) {
                g2.setColor(outline);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class RoundField extends JTextField {
        private final String hint;

        RoundField(String hint) {
            this.hint = hint;
            setOpaque(false);
            setFont(new Font(FONT, Font.PLAIN, 14));
            setForeground(TEXT);
            setCaretColor(NAVY);
            setBorder(new EmptyBorder(9, 14, 9, 14));
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(isEnabled() ? Color.WHITE : new Color(0xEEEEEE));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g2.setColor(hasFocus() ? NAVY_LIGHT : LINE);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g2.dispose();
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Graphics2D h = (Graphics2D) g.create();
                smooth(h);
                h.setColor(new Color(0xA5ADB9));
                h.setFont(getFont());
                FontMetrics fm = h.getFontMetrics();
                h.drawString(hint, getInsets().left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                h.dispose();
            }
        }
    }

    static class DocListener implements DocumentListener {
        private final Runnable action;

        DocListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            action.run();
        }
    }

    static String receipt(Booking b) {
        String bar = "==========================================\n";
        StringBuilder s = new StringBuilder();
        s.append(bar);
        s.append("            ").append(HOTEL.toUpperCase()).append("\n");
        s.append("              BOOKING DETAILS\n");
        s.append(bar);
        row(s, "Booking ID", b.id);
        row(s, "Status", b.status.name());
        row(s, "Booked On", b.bookedOn.format(DATE_TIME));
        s.append("\n GUEST\n");
        row(s, "Name", b.guestName);
        row(s, "Phone", b.phone);
        row(s, "Email", b.email);
        s.append("\n STAY\n");
        row(s, "Room", b.roomNumber + " (" + b.type.label + ")");
        row(s, "Check-in", b.checkIn.format(DATE));
        row(s, "Check-out", b.checkOut.format(DATE));
        row(s, "Nights", String.valueOf(b.nights()));
        row(s, "Guests", String.valueOf(b.guests));
        s.append("\n PAYMENT\n");
        row(s, "Room Charges", money(b.roomCharge));
        row(s, "GST (12%)", money(b.tax));
        row(s, "Total Amount", money(b.total));
        row(s, "Payment Mode", b.paymentMethod);
        row(s, "Transaction ID", b.transactionId);
        if (b.status == Status.CANCELLED) {
            row(s, "Refund", money(b.refund));
        }
        s.append(bar);
        return s.toString();
    }

    private static void row(StringBuilder s, String key, String value) {
        s.append(String.format(" %-15s: %s%n", key, value));
    }

    static class BookingDetailsDialog extends JDialog {
        BookingDetailsDialog(Window owner, Booking b, String heading, Color headingColor) {
            super(owner, "Booking Details", ModalityType.APPLICATION_MODAL);
            setLayout(new BorderLayout());
            JPanel banner = new JPanel(new BorderLayout());
            banner.setBackground(headingColor);
            banner.setBorder(new EmptyBorder(12, 18, 12, 18));
            banner.add(label(heading, Font.BOLD, 18, Color.WHITE), BorderLayout.CENTER);
            add(banner, BorderLayout.NORTH);

            JTextArea area = new JTextArea(receipt(b));
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            area.setBackground(new Color(0xFFFDF7));
            area.setForeground(TEXT);
            area.setBorder(new EmptyBorder(14, 18, 14, 18));
            JScrollPane sp = new JScrollPane(area);
            sp.setBorder(null);
            add(sp, BorderLayout.CENTER);

            RoundButton close = new RoundButton("Close", NAVY, NAVY_LIGHT, Color.WHITE, 22, null);
            close.addActionListener(e -> dispose());
            JPanel foot = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));
            foot.setBackground(BG);
            foot.add(close);
            add(foot, BorderLayout.SOUTH);

            setSize(500, 600);
            setLocationRelativeTo(owner);
        }
    }

    static class PaymentDialog extends JDialog {
        private static final String[] METHODS = {"Credit / Debit Card", "UPI", "Net Banking", "Pay at Hotel"};
        private final double amount;
        private final JComboBox<String> methodBox = new JComboBox<>(METHODS);
        private final CardLayout cards = new CardLayout();
        private final JPanel cardHolder = new JPanel(cards);
        private final RoundField cardNo = new RoundField("1234 5678 9012 3456");
        private final RoundField cardName = new RoundField("Name on card");
        private final RoundField expiry = new RoundField("MM/YY");
        private final RoundField cvv = new RoundField("123");
        private final RoundField upi = new RoundField("yourname@bank");
        private final JComboBox<String> bank = new JComboBox<>(new String[]{"State Bank of India", "HDFC Bank",
                "ICICI Bank", "Axis Bank", "Punjab National Bank", "Kotak Mahindra Bank"});
        private final JLabel error = label(" ", Font.BOLD, 12, RED);
        private final JProgressBar bar = new JProgressBar();
        private final RoundButton payButton;
        private final RoundButton backButton = new RoundButton("Back", Color.WHITE, SELECT, NAVY, 22, NAVY);
        private PaymentResult result;

        PaymentDialog(Window owner, double amount) {
            super(owner, "Secure Payment", ModalityType.APPLICATION_MODAL);
            this.amount = amount;
            payButton = new RoundButton("Pay " + money(amount), GOLD, GOLD_DARK, TEXT, 22, null);
            getContentPane().setBackground(BG);
            setLayout(new BorderLayout());

            JPanel top = new JPanel(new BorderLayout());
            top.setBackground(NAVY);
            top.setBorder(new EmptyBorder(14, 20, 14, 20));
            top.add(label("Amount to pay", Font.PLAIN, 12, new Color(0xC9D8F5)), BorderLayout.NORTH);
            top.add(label(money(amount), Font.BOLD, 26, Color.WHITE), BorderLayout.CENTER);
            add(top, BorderLayout.NORTH);

            styleCombo(methodBox);
            styleCombo(bank);
            bank.setSelectedIndex(-1);
            bank.setPrototypeDisplayValue("Punjab National Bank");

            JPanel cardPanel = new JPanel(new GridLayout(0, 1, 0, 10));
            cardPanel.setOpaque(false);
            JPanel small = new JPanel(new GridLayout(1, 2, 12, 0));
            small.setOpaque(false);
            small.add(labeled("Expiry", expiry));
            small.add(labeled("CVV", cvv));
            cardPanel.add(labeled("Card number", cardNo));
            cardPanel.add(labeled("Name on card", cardName));
            cardPanel.add(small);

            JPanel upiPanel = new JPanel(new BorderLayout(0, 10));
            upiPanel.setOpaque(false);
            upiPanel.add(labeled("UPI ID", upi), BorderLayout.NORTH);

            JPanel bankPanel = new JPanel(new BorderLayout(0, 10));
            bankPanel.setOpaque(false);
            bankPanel.add(labeled("Select your bank", bank), BorderLayout.NORTH);

            JPanel hotelPanel = new JPanel(new BorderLayout());
            hotelPanel.setOpaque(false);
            JLabel note = new JLabel("<html>No payment is needed now. Please pay the full amount at the reception during check-in.</html>");
            note.setFont(new Font(FONT, Font.PLAIN, 13));
            note.setForeground(TEXT);
            hotelPanel.add(note, BorderLayout.NORTH);

            cardHolder.setOpaque(false);
            cardHolder.add(cardPanel, METHODS[0]);
            cardHolder.add(upiPanel, METHODS[1]);
            cardHolder.add(bankPanel, METHODS[2]);
            cardHolder.add(hotelPanel, METHODS[3]);
            methodBox.addActionListener(e -> {
                cards.show(cardHolder, (String) methodBox.getSelectedItem());
                error.setText(" ");
            });

            JLabel demo = new JLabel("<html>Demo mode: no real payment is made. Any 16-digit card works; a card ending in 0000 is declined.</html>");
            demo.setFont(new Font(FONT, Font.ITALIC, 11));
            demo.setForeground(MUTED);

            bar.setVisible(false);
            bar.setPreferredSize(new Dimension(100, 6));

            JPanel body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setBorder(new EmptyBorder(16, 22, 8, 22));
            JPanel methodRow = labeled("Payment method", methodBox);
            methodRow.setAlignmentX(0f);
            cardHolder.setAlignmentX(0f);
            error.setAlignmentX(0f);
            demo.setAlignmentX(0f);
            bar.setAlignmentX(0f);
            body.add(methodRow);
            body.add(Box.createVerticalStrut(12));
            body.add(cardHolder);
            body.add(Box.createVerticalStrut(6));
            body.add(error);
            body.add(Box.createVerticalStrut(4));
            body.add(demo);
            body.add(Box.createVerticalStrut(8));
            body.add(bar);
            add(body, BorderLayout.CENTER);

            JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
            foot.setOpaque(false);
            foot.add(backButton);
            foot.add(payButton);
            add(foot, BorderLayout.SOUTH);

            backButton.addActionListener(e -> dispose());
            payButton.addActionListener(e -> pay());
            getRootPane().setDefaultButton(payButton);

            setSize(460, 520);
            setResizable(false);
            setLocationRelativeTo(owner);
        }

        PaymentResult result() {
            return result;
        }

        private Map<String, String> details() {
            Map<String, String> d = new HashMap<>();
            d.put("number", cardNo.getText());
            d.put("name", cardName.getText());
            d.put("expiry", expiry.getText());
            d.put("cvv", cvv.getText());
            d.put("upi", upi.getText());
            d.put("bank", bank.getSelectedItem() == null ? "" : bank.getSelectedItem().toString());
            return d;
        }

        private void setBusy(boolean busy) {
            payButton.setEnabled(!busy);
            backButton.setEnabled(!busy);
            methodBox.setEnabled(!busy);
            cardNo.setEnabled(!busy);
            cardName.setEnabled(!busy);
            expiry.setEnabled(!busy);
            cvv.setEnabled(!busy);
            upi.setEnabled(!busy);
            bank.setEnabled(!busy);
            bar.setVisible(busy);
            bar.setIndeterminate(busy);
        }

        private void pay() {
            String method = (String) methodBox.getSelectedItem();
            Map<String, String> d = details();
            String err = PaymentGateway.validate(method, d);
            if (err != null) {
                error.setText(err);
                return;
            }
            error.setForeground(NAVY);
            error.setText("Processing payment securely, please wait...");
            setBusy(true);
            javax.swing.Timer t = new javax.swing.Timer(1800, e -> {
                PaymentResult r = PaymentGateway.charge(method, d, amount);
                setBusy(false);
                if (r.success) {
                    result = r;
                    dispose();
                } else {
                    error.setForeground(RED);
                    error.setText(r.message);
                }
            });
            t.setRepeats(false);
            t.start();
        }
    }

    static class BookingDialog extends JDialog {
        private final HotelService service;
        private final Room room;
        private final LocalDate in, out;
        private final int guests;
        private final RoundField nameField = new RoundField("Full name");
        private final RoundField phoneField = new RoundField("10-digit mobile number");
        private final RoundField emailField = new RoundField("name@example.com");
        private final JLabel error = label(" ", Font.BOLD, 12, RED);
        private Booking result;

        BookingDialog(Window owner, HotelService service, Room room, LocalDate in, LocalDate out, int guests) {
            super(owner, "Book Room " + room.number, ModalityType.APPLICATION_MODAL);
            this.service = service;
            this.room = room;
            this.in = in;
            this.out = out;
            this.guests = guests;
            getContentPane().setBackground(BG);
            setLayout(new BorderLayout());

            JPanel top = new JPanel(new BorderLayout());
            top.setBackground(NAVY);
            top.setBorder(new EmptyBorder(14, 22, 14, 22));
            top.add(label("Room " + room.number + "  -  " + room.type.label, Font.BOLD, 20, Color.WHITE), BorderLayout.CENTER);
            top.add(label(room.type.amenities, Font.PLAIN, 12, new Color(0xC9D8F5)), BorderLayout.SOUTH);
            add(top, BorderLayout.NORTH);

            Quote q = HotelService.quote(room.type, in, out);
            JPanel summary = new JPanel(new GridLayout(0, 2, 8, 6));
            summary.setBackground(Color.WHITE);
            summary.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(12, 14, 12, 14)));
            addSummary(summary, "Check-in", in.format(DATE), false);
            addSummary(summary, "Check-out", out.format(DATE), false);
            addSummary(summary, "Nights / Guests", q.nights + " night(s) / " + guests + " guest(s)", false);
            addSummary(summary, "Room charges", money(q.roomCharge), false);
            addSummary(summary, "GST (12%)", money(q.tax), false);
            addSummary(summary, "Total payable", money(q.total), true);

            JPanel form = new JPanel(new GridLayout(0, 1, 0, 10));
            form.setOpaque(false);
            form.add(labeled("Guest name", nameField));
            form.add(labeled("Phone number", phoneField));
            form.add(labeled("Email address", emailField));

            JPanel body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setBorder(new EmptyBorder(16, 22, 6, 22));
            body.add(label("Guest details", Font.BOLD, 15, NAVY));
            body.add(Box.createVerticalStrut(8));
            body.add(form);
            body.add(Box.createVerticalStrut(6));
            body.add(error);
            body.add(Box.createVerticalStrut(6));
            body.add(label("Booking summary", Font.BOLD, 15, NAVY));
            body.add(Box.createVerticalStrut(8));
            body.add(summary);
            for (Component c : body.getComponents()) {
                if (c instanceof JComponent) ((JComponent) c).setAlignmentX(0f);
            }
            add(body, BorderLayout.CENTER);

            RoundButton cancel = new RoundButton("Cancel", Color.WHITE, SELECT, NAVY, 22, NAVY);
            RoundButton next = new RoundButton("Continue to Payment", GOLD, GOLD_DARK, TEXT, 22, null);
            cancel.addActionListener(e -> dispose());
            next.addActionListener(e -> proceed());
            JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
            foot.setOpaque(false);
            foot.add(cancel);
            foot.add(next);
            add(foot, BorderLayout.SOUTH);
            getRootPane().setDefaultButton(next);

            setSize(480, 640);
            setResizable(false);
            setLocationRelativeTo(owner);
        }

        private void addSummary(JPanel p, String k, String v, boolean bold) {
            p.add(label(k, Font.PLAIN, 13, MUTED));
            p.add(label(v, bold ? Font.BOLD : Font.PLAIN, bold ? 15 : 13, bold ? NAVY : TEXT));
        }

        Booking result() {
            return result;
        }

        private void proceed() {
            String name = nameField.getText().trim();
            String phone = phoneField.getText().trim();
            String email = emailField.getText().trim();
            if (name.length() < 2) {
                error.setText("Please enter the guest name.");
                return;
            }
            if (!phone.matches("\\d{10}")) {
                error.setText("Phone number must be exactly 10 digits.");
                return;
            }
            if (!email.matches("^[\\w.+\\-]+@[\\w\\-]+(\\.[\\w\\-]+)+$")) {
                error.setText("Please enter a valid email address.");
                return;
            }
            error.setText(" ");
            Quote q = HotelService.quote(room.type, in, out);
            PaymentDialog pd = new PaymentDialog(this, q.total);
            pd.setVisible(true);
            PaymentResult pr = pd.result();
            if (pr == null || !pr.success) return;
            try {
                result = service.book(name, phone, email, room, in, out, guests, pr.method, pr.transactionId);
                dispose();
            } catch (IllegalStateException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Booking failed", JOptionPane.ERROR_MESSAGE);
                dispose();
            }
        }
    }

    static class MainWindow extends JFrame {
        private final HotelService service = new HotelService();
        private final JLabel statusBar = label(" ", Font.PLAIN, 12, MUTED);
        private SearchPanel searchPanel;
        private BookingsPanel bookingsPanel;
        private RoomsPanel roomsPanel;

        MainWindow() {
            super(HOTEL + " - Reservation System");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(1020, 700);
            setMinimumSize(new Dimension(860, 560));
            setLocationRelativeTo(null);
            getContentPane().setBackground(BG);
            setLayout(new BorderLayout());

            searchPanel = new SearchPanel();
            bookingsPanel = new BookingsPanel();
            roomsPanel = new RoomsPanel();

            JTabbedPane tabs = new JTabbedPane();
            tabs.setFont(new Font(FONT, Font.BOLD, 14));
            tabs.addTab("  Search & Book  ", searchPanel);
            tabs.addTab("  My Bookings  ", bookingsPanel);
            tabs.addTab("  Rooms & Rates  ", roomsPanel);

            JPanel foot = new JPanel(new BorderLayout());
            foot.setBackground(Color.WHITE);
            foot.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, LINE), new EmptyBorder(6, 16, 6, 16)));
            foot.add(statusBar, BorderLayout.WEST);

            add(buildHeader(), BorderLayout.NORTH);
            add(tabs, BorderLayout.CENTER);
            add(foot, BorderLayout.SOUTH);
            updateStatus();
        }

        private JComponent buildHeader() {
            GradientPanel header = new GradientPanel();
            header.setBorder(new EmptyBorder(16, 24, 16, 24));
            JPanel names = new JPanel(new GridLayout(2, 1, 0, 2));
            names.setOpaque(false);
            JLabel title = new JLabel(HOTEL.toUpperCase());
            title.setFont(new Font("Serif", Font.BOLD, 26));
            title.setForeground(Color.WHITE);
            names.add(title);
            names.add(label("Room Reservation & Booking Management", Font.ITALIC, 13, new Color(0xF0D98A)));
            header.add(names, BorderLayout.WEST);
            JLabel date = label("Today: " + LocalDate.now().format(DATE), Font.PLAIN, 13, new Color(0xD6E4FF));
            header.add(date, BorderLayout.EAST);
            return header;
        }

        private void updateStatus() {
            statusBar.setText("Rooms: " + service.rooms().size() + "   |   Active bookings: " + service.activeBookings()
                    + "   |   Data folder: hotel_data");
        }

        private void dataChanged() {
            bookingsPanel.reload();
            roomsPanel.reload();
            searchPanel.refreshResults();
            updateStatus();
        }

        class SearchPanel extends JPanel {
            private final LocalDate today = LocalDate.now();
            private final JSpinner inSpin = dateSpinner(today, today);
            private final JSpinner outSpin = dateSpinner(today.plusDays(1), today.plusDays(1));
            private final JSpinner guestSpin = new JSpinner(new SpinnerNumberModel(1, 1, 4, 1));
            private final JComboBox<Object> typeBox = new JComboBox<>(new Object[]{"All Categories",
                    RoomType.STANDARD, RoomType.DELUXE, RoomType.SUITE});
            private final DefaultTableModel model = new DefaultTableModel(new String[]{"Room", "Category", "Capacity",
                    "Amenities", "Price / Night", "Total (incl. GST)"}, 0) {
                @Override
                public boolean isCellEditable(int r, int c) {
                    return false;
                }
            };
            private final JTable table = new JTable(model);
            private final JLabel summary = label("Choose your dates and press Search to see available rooms.", Font.PLAIN, 13, MUTED);
            private List<Room> results = new ArrayList<>();
            private boolean searched;
            private LocalDate lastIn, lastOut;
            private int lastGuests;
            private RoomType lastType;

            SearchPanel() {
                setLayout(new BorderLayout(0, 14));
                setBackground(BG);
                setBorder(new EmptyBorder(16, 18, 14, 18));

                guestSpin.setFont(new Font(FONT, Font.PLAIN, 14));
                guestSpin.setPreferredSize(new Dimension(80, 36));
                styleCombo(typeBox);
                inSpin.addChangeListener(e -> {
                    LocalDate in = toLocal(inSpin);
                    if (!toLocal(outSpin).isAfter(in)) setDate(outSpin, in.plusDays(1));
                });

                RoundButton searchBtn = new RoundButton("Search Rooms", NAVY, NAVY_LIGHT, Color.WHITE, 22, null);
                searchBtn.addActionListener(e -> search());

                JPanel card = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
                card.setBackground(Color.WHITE);
                card.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(8, 8, 8, 8)));
                card.add(labeled("Check-in", inSpin));
                card.add(labeled("Check-out", outSpin));
                card.add(labeled("Guests", guestSpin));
                card.add(labeled("Room category", typeBox));
                JPanel btnWrap = new JPanel(new BorderLayout());
                btnWrap.setOpaque(false);
                btnWrap.add(Box.createVerticalStrut(18), BorderLayout.NORTH);
                btnWrap.add(searchBtn, BorderLayout.CENTER);
                card.add(btnWrap);
                add(card, BorderLayout.NORTH);

                styleTable(table, -1, 60, 90, 90, 330, 110, 130);
                table.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() == 2) bookSelected();
                    }
                });
                add(tableScroll(table), BorderLayout.CENTER);

                RoundButton bookBtn = new RoundButton("Book Selected Room", GOLD, GOLD_DARK, TEXT, 22, null);
                bookBtn.addActionListener(e -> bookSelected());
                JPanel bottom = new JPanel(new BorderLayout());
                bottom.setOpaque(false);
                bottom.add(summary, BorderLayout.CENTER);
                bottom.add(bookBtn, BorderLayout.EAST);
                add(bottom, BorderLayout.SOUTH);

                lastIn = today;
                lastOut = today.plusDays(1);
                lastGuests = 1;
                lastType = null;
                searched = true;
                refreshResults();
            }

            private void search() {
                LocalDate in = toLocal(inSpin);
                LocalDate out = toLocal(outSpin);
                if (in.isBefore(LocalDate.now())) {
                    JOptionPane.showMessageDialog(this, "Check-in date cannot be in the past.", "Invalid dates", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (!out.isAfter(in)) {
                    JOptionPane.showMessageDialog(this, "Check-out must be after the check-in date.", "Invalid dates", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                Object sel = typeBox.getSelectedItem();
                lastType = sel instanceof RoomType ? (RoomType) sel : null;
                lastIn = in;
                lastOut = out;
                lastGuests = (Integer) guestSpin.getValue();
                searched = true;
                refreshResults();
            }

            void refreshResults() {
                if (!searched) return;
                results = service.search(lastIn, lastOut, lastGuests, lastType);
                model.setRowCount(0);
                for (Room r : results) {
                    Quote q = HotelService.quote(r.type, lastIn, lastOut);
                    model.addRow(new Object[]{r.number, r.type.label, r.type.capacity + " guests", r.type.amenities,
                            money(r.type.price), money(q.total)});
                }
                long nights = ChronoUnit.DAYS.between(lastIn, lastOut);
                if (results.isEmpty()) {
                    summary.setForeground(RED);
                    summary.setText("No rooms available for these dates. Try different dates or another category.");
                } else {
                    summary.setForeground(GREEN);
                    summary.setText(results.size() + " room(s) available for " + nights + " night(s). Select a room and click Book.");
                }
            }

            private void bookSelected() {
                int row = table.getSelectedRow();
                if (!searched || row < 0 || row >= results.size()) {
                    JOptionPane.showMessageDialog(this, "Please search and select a room from the list first.",
                            "No room selected", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                Room room = results.get(row);
                BookingDialog dialog = new BookingDialog(MainWindow.this, service, room, lastIn, lastOut, lastGuests);
                dialog.setVisible(true);
                Booking b = dialog.result();
                if (b != null) {
                    dataChanged();
                    new BookingDetailsDialog(MainWindow.this, b, "Booking Confirmed!", GREEN).setVisible(true);
                }
            }
        }

        class BookingsPanel extends JPanel {
            private final RoundField searchField = new RoundField("Search by booking ID, guest name or phone...");
            private final JComboBox<String> statusBox = new JComboBox<>(new String[]{"All", "Confirmed", "Cancelled"});
            private final DefaultTableModel model = new DefaultTableModel(new String[]{"Booking ID", "Guest", "Phone",
                    "Room", "Category", "Check-in", "Check-out", "Total", "Status"}, 0) {
                @Override
                public boolean isCellEditable(int r, int c) {
                    return false;
                }
            };
            private final JTable table = new JTable(model);
            private List<Booking> shown = new ArrayList<>();

            BookingsPanel() {
                setLayout(new BorderLayout(0, 14));
                setBackground(BG);
                setBorder(new EmptyBorder(16, 18, 14, 18));

                styleCombo(statusBox);
                searchField.setPreferredSize(new Dimension(360, 38));
                searchField.getDocument().addDocumentListener(new DocListener(this::reload));
                statusBox.addActionListener(e -> reload());

                JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
                top.setOpaque(false);
                top.add(searchField);
                top.add(statusBox);
                add(top, BorderLayout.NORTH);

                styleTable(table, 8, 90, 140, 100, 60, 90, 100, 100, 110, 100);
                table.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() == 2) viewSelected();
                    }
                });
                add(tableScroll(table), BorderLayout.CENTER);

                RoundButton view = new RoundButton("View Details", NAVY, NAVY_LIGHT, Color.WHITE, 22, null);
                RoundButton cancel = new RoundButton("Cancel Booking", RED, new Color(0x9E2E23), Color.WHITE, 22, null);
                RoundButton refresh = new RoundButton("Refresh", Color.WHITE, SELECT, NAVY, 22, NAVY);
                view.addActionListener(e -> viewSelected());
                cancel.addActionListener(e -> cancelSelected());
                refresh.addActionListener(e -> reload());
                JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
                bottom.setOpaque(false);
                bottom.add(refresh);
                bottom.add(view);
                bottom.add(cancel);
                add(bottom, BorderLayout.SOUTH);
                reload();
            }

            void reload() {
                String q = searchField.getText().trim().toLowerCase();
                String st = (String) statusBox.getSelectedItem();
                shown = new ArrayList<>();
                model.setRowCount(0);
                for (Booking b : service.bookings()) {
                    if (!"All".equals(st) && !b.status.name().equalsIgnoreCase(st)) continue;
                    if (!q.isEmpty() && !(b.id.toLowerCase().contains(q) || b.guestName.toLowerCase().contains(q)
                            || b.phone.contains(q))) continue;
                    shown.add(b);
                    model.addRow(new Object[]{b.id, b.guestName, b.phone, b.roomNumber, b.type.label,
                            b.checkIn.format(DATE), b.checkOut.format(DATE), money(b.total), b.status.name()});
                }
            }

            private Booking selected() {
                int row = table.getSelectedRow();
                if (row < 0 || row >= shown.size()) {
                    JOptionPane.showMessageDialog(this, "Please select a booking from the list first.",
                            "No booking selected", JOptionPane.INFORMATION_MESSAGE);
                    return null;
                }
                return shown.get(row);
            }

            private void viewSelected() {
                Booking b = selected();
                if (b == null) return;
                boolean ok = b.status == Status.CONFIRMED;
                new BookingDetailsDialog(MainWindow.this, b, ok ? "Booking Confirmed" : "Booking Cancelled", ok ? NAVY : RED)
                        .setVisible(true);
            }

            private void cancelSelected() {
                Booking b = selected();
                if (b == null) return;
                if (b.status == Status.CANCELLED) {
                    JOptionPane.showMessageDialog(this, "This booking is already cancelled.", "Cancel Booking", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                if (!service.canCancel(b)) {
                    JOptionPane.showMessageDialog(this, "This stay is already completed and cannot be cancelled.",
                            "Cancel Booking", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                double refund = service.refundFor(b);
                String policy = "Refund policy: 2+ days before check-in = 100%, 1 day before = 50%, later = no refund.";
                String msg = "Cancel booking " + b.id + " for room " + b.roomNumber + " (" + b.checkIn.format(DATE) + " to "
                        + b.checkOut.format(DATE) + ")?\n\nRefund amount: " + money(refund) + "\n" + policy;
                int choice = JOptionPane.showConfirmDialog(this, msg, "Confirm Cancellation", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) return;
                try {
                    double given = service.cancel(b.id);
                    dataChanged();
                    String done = given > 0
                            ? "Booking cancelled. " + money(given) + " will be refunded to your original payment method in 5-7 days."
                            : "Booking cancelled. No refund is applicable for this booking.";
                    JOptionPane.showMessageDialog(this, done, "Cancelled", JOptionPane.INFORMATION_MESSAGE);
                } catch (IllegalStateException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        class RoomsPanel extends JPanel {
            private final DefaultTableModel model = new DefaultTableModel(new String[]{"Room", "Floor", "Category",
                    "Capacity", "Price / Night", "Amenities", "Today"}, 0) {
                @Override
                public boolean isCellEditable(int r, int c) {
                    return false;
                }
            };
            private final JTable table = new JTable(model);
            private final JLabel[] counts = new JLabel[RoomType.values().length];

            RoomsPanel() {
                setLayout(new BorderLayout(0, 14));
                setBackground(BG);
                setBorder(new EmptyBorder(16, 18, 14, 18));

                JPanel cards = new JPanel(new GridLayout(1, 3, 14, 0));
                cards.setOpaque(false);
                for (RoomType t : RoomType.values()) {
                    cards.add(categoryCard(t));
                }
                add(cards, BorderLayout.NORTH);

                styleTable(table, 6, 60, 60, 90, 90, 110, 330, 90);
                add(tableScroll(table), BorderLayout.CENTER);
                reload();
            }

            private JPanel categoryCard(RoomType t) {
                JPanel p = new JPanel();
                p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
                p.setBackground(Color.WHITE);
                p.setBorder(new CompoundBorder(new MatteBorder(4, 0, 0, 0, GOLD),
                        new CompoundBorder(new LineBorder(LINE), new EmptyBorder(10, 14, 10, 14))));
                JLabel name = label(t.label, Font.BOLD, 17, NAVY);
                JLabel price = label(money(t.price) + " / night", Font.BOLD, 14, GOLD_DARK);
                JLabel info = new JLabel("<html><body style='width:210px'>Up to " + t.capacity + " guests. " + t.amenities + "</body></html>");
                info.setFont(new Font(FONT, Font.PLAIN, 12));
                info.setForeground(TEXT);
                counts[t.ordinal()] = label(" ", Font.PLAIN, 12, MUTED);
                for (JComponent c : new JComponent[]{name, price, info, counts[t.ordinal()]}) {
                    c.setAlignmentX(0f);
                    p.add(c);
                    p.add(Box.createVerticalStrut(4));
                }
                return p;
            }

            void reload() {
                model.setRowCount(0);
                int[] total = new int[RoomType.values().length];
                int[] free = new int[RoomType.values().length];
                for (Room r : service.rooms()) {
                    String st = service.todayStatus(r);
                    total[r.type.ordinal()]++;
                    if ("Available".equals(st)) free[r.type.ordinal()]++;
                    model.addRow(new Object[]{r.number, r.floor(), r.type.label, r.type.capacity + " guests",
                            money(r.type.price), r.type.amenities, st});
                }
                for (RoomType t : RoomType.values()) {
                    counts[t.ordinal()].setText(total[t.ordinal()] + " rooms  |  " + free[t.ordinal()] + " available today");
                }
            }
        }
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            UIManager.put("OptionPane.messageFont", new Font(FONT, Font.PLAIN, 14));
            UIManager.put("OptionPane.buttonFont", new Font(FONT, Font.PLAIN, 13));
            try {
                new MainWindow().setVisible(true);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Startup Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}