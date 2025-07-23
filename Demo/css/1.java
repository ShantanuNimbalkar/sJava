public class Example {

    public static void main(String[] args) {
        int x = 10;
        int y = 0;

        // ❌ Unused variable
        int unused = 100;

        // ❌ System.out.println used in production code
        System.out.println("Value of x: " + x);

        // ❌ Potential division by zero
        int result = x / y;
        System.out.println("Result: " + result);

        // ❌ Magic number
        if (x == 42) {
            System.out.println("The answer to everything!");
        }

        // ❌ Long method (too much in main)
        for (int i = 0; i < 5; i++) {
            System.out.println("Loop: " + i);
        }

        Example obj = new Example();
        obj.doNothing(); // ❌ Method with no real purpose
    }

    // ❌ Empty method
    public void doNothing() {
    }

    // ❌ Method not following naming convention (should be camelCase)
    public void Bad_Method_Name() {
        System.out.println("Bad method name.");
    }

    // ❌ No Javadoc comment
    public int add(int a, int b){
        return a + b;
    }
}
