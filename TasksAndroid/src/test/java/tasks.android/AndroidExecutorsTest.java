package tasks.android;

import android.os.Handler;

import java.util.concurrent.Executor;

import static org.junit.Assert.*;

/**
 * Created by Arash on 9/13/2015.
 */
public class AndroidExecutorsTest {

    @org.junit.Test
    public void testFromHandler() throws Exception {
        AndroidExecutors.from(new Handler());
    }
}