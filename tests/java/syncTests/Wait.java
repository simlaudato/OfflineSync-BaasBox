package syncTests;

import java.util.Date;

/**
 * Created by Simone on 10/09/2014.
 */
public class Wait {

    public static void wait2seconds(){
        Date start = new Date();
        Date end = new Date();
        while (end.getTime() - start.getTime() < 2 * 1000) {
            end = new Date();
        }
    }

    public static void sleep2seconds(){
        Thread current = Thread.currentThread();
        try {
            current.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
