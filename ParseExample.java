public class ParseExample {
    public static void main(String[] args) {
        String input = "abc123";  // Invalid number string

        try {
            int number = Integer.parseInt(input);  // This will fail
            System.out.println("Parsed number: " + number);
        } catch (NumberFormatException e) {
            System.out.println("Parsing failed: " + e.getMessage());
        }
    }
}
