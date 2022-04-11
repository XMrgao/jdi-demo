public class Main {
    public static int value;

    public static void main(String[] args) throws InterruptedException {
        while (true){
            Thread.sleep(1000);
            new AAA().run();
            System.out.println("value:"+value);
        }
    }


    static class AAA {
        public void run(){
            new BBB().run();
        }
    }

    static class BBB {
        public void run(){
            new CCC().run();
        }
    }

    static class CCC {
        public void run(){
            Main.value ++;
        }
    }
}
