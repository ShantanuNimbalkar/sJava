public class Outer {
 void method(){
  Inner ic = new Inner();//Causes generation of accessor class
  System.out.println("Hello");
  int unusedVariable=100;
 }
 public class Inner {
  private Inner(){}
 }
}
