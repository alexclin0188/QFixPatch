package alexclin.qfix.qfixgradle.bug;

import android.os.Bundle;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import alexclin.qfix.qfixgradle.R;
/**
 * TestActivity
 *
 * @author alexclin  2017/1/14 14:12
 */

public class TestActivity extends TestActivity2 {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_main);
        TextView tv = (TextView) findViewById(R.id.tv_test);
        StringBuilder builder = new StringBuilder();
        appInfoBase(builder);
        appInfo1(builder);
        appInfo2(builder);
        appendInfo(builder);
        new FinalBug().appendBugMsg(builder);
        tv.setText(builder.toString());
    }

    private void appendInfo(StringBuilder builder){
        builder.append("\nbug origin from class TestActivity");
    }
}
