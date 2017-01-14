package alexclin.qfix.qfixgradle.bug;

/**
 * Bug111
 *
 * @author alexclin  2017/1/14 18:21
 */

public abstract class Bug111 extends BaseBug {
    protected void appendBugMsg(StringBuilder builder) {
        if (builder != null){
            super.appendBugMsg(builder);
            builder.append("\nbug msg from Bug111--------");
        }
    }
}
