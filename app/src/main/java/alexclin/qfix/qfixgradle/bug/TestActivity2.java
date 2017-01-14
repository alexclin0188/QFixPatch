package alexclin.qfix.qfixgradle.bug;

/**
 * TestActivity2
 *
 * @author alexclin  2017/1/14 16:04
 */

public abstract class TestActivity2 extends TestActivity1 {

    protected void appInfo2(StringBuilder builder){
        if(builder!=null) builder.append("\nbug msg from TestActivity2");
    }

}
