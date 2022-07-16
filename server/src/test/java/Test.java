import org.jmlspecs.annotation.NonNull;
import org.jmlspecs.openjml.Factory;
import org.jmlspecs.openjml.IAPI;

import java.io.File;
import java.io.PrintWriter;

/**
 * @author Alexander Weigl
 * @version 1 (15.07.22)
 */
public class Test {
    @org.junit.Test
    public void test() throws Exception {
        @NonNull IAPI api = Factory.makeAPI(new PrintWriter(System.out), diagnostic -> {
            System.out.println(diagnostic);
        }, null);
        api.parseAndCheck(new File("/home/weigl/work/jml-vscode/test/Error.java"));
    }
}
