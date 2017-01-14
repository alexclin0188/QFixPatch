package alexclin.qfix.qfixgradle.bug;

/**
 * FinalBug
 *
 * @author alexclin  2017/1/14 18:22
 */

public final class FinalBug extends Bug111{

    protected void appendBugMsg(StringBuilder builder) {
        if (builder != null){
            super.appendBugMsg(builder);
            builder.append("\nbug msg from BaseBug--------");
        }
    }

}
