package alexclin.qfix.qfixgradle.second;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.TextView;

import alexclin.qfix.qfixgradle.R;

/**
 * @author xiehonglin429 on 2017/6/22.
 */

public class SecondDexActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_second);
        TextView bug1 = (TextView) findViewById(R.id.tv_second_bug1);
        TextView bug2 = (TextView) findViewById(R.id.tv_second_bug2);
        bug1.setText("Origin 状态");
        bug2.setText(new FinalBug().getTestFinalBugMsg());
    }
}
