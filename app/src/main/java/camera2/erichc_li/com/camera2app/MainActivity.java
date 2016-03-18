package camera2.erichc_li.com.camera2app;

import android.graphics.ImageFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getName();

    private Camera2Preview mCamera2Preview;
    private FrameLayout mFrameLayout;

    private HandlerThread mCaptureThread;
    private Handler mCaptureHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        initBackThread();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                mCamera2Preview.takePicture();

            }
        });

        mFrameLayout = (FrameLayout) findViewById(R.id.camera_textureview);

        mCamera2Preview = new Camera2Preview(this, ImageFormat.JPEG);
        mFrameLayout.addView(mCamera2Preview);

    }

    private void initBackThread()
    {
        mCaptureThread = new HandlerThread("check-Capture-Thread");
        mCaptureThread.start();
        mCaptureHandler = new Handler(mCaptureThread.getLooper());
    }

    private void stopBackThread() {

        mCaptureThread.quitSafely();
        try {
            mCaptureThread.join();
            mCaptureThread = null;
            mCaptureHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        stopBackThread();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        mFrameLayout.removeAllViews();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.format1:
                mCamera2Preview = new Camera2Preview(this, ImageFormat.JPEG);
                mFrameLayout.addView(mCamera2Preview);
                return true;
            case R.id.format2:
                mCamera2Preview = new Camera2Preview(this, ImageFormat.RAW_SENSOR);
                mFrameLayout.addView(mCamera2Preview);
                return true;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

}
