public class Outer {
 void method(){
  Inner ic = new Inner();//Causes generation of accessor class
  System.out.println("Hello");
  int unusedVariable=100;
  int xy = 100;
 }
 public class Inner {
  private Inner(){
   int kj=0;
   System.out.println("Hello");
  }
 }
}
