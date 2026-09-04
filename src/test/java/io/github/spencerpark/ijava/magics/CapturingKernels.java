package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.ijava.IJava;
import io.github.spencerpark.ijava.JavaKernel;
import io.github.spencerpark.jupyter.kernel.display.DisplayData;
import io.github.spencerpark.jupyter.kernel.display.mime.MIMEType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public final class CapturingKernels {
    private static final CapturingKernel KERNEL = new CapturingKernel();

    private CapturingKernels() {
    }

    public static CapturingKernel kernel() {
        return KERNEL;
    }

    public static CapturingKernel install() throws Exception {
        Field field = IJava.class.getDeclaredField("kernel");
        field.setAccessible(true);
        field.set(null, KERNEL);
        return KERNEL;
    }

    public static void uninstall() throws Exception {
        Field field = IJava.class.getDeclaredField("kernel");
        field.setAccessible(true);
        field.set(null, null);
    }

    public static class CapturingKernel extends JavaKernel {
        private final List<DisplayData> displays = new CopyOnWriteArrayList<>();
        private final List<String> evaluated = new CopyOnWriteArrayList<>();
        private Function<String, Object> evaluationResult = expression -> null;

        public void reset() {
            displays.clear();
            evaluated.clear();
            evaluationResult = expression -> null;
        }

        public void setEvaluationResult(Function<String, Object> evaluationResult) {
            this.evaluationResult = evaluationResult;
        }

        public List<DisplayData> displays() {
            return List.copyOf(displays);
        }

        public List<String> evaluated() {
            return List.copyOf(evaluated);
        }

        public List<String> displayedTexts() {
            List<String> texts = new ArrayList<>();
            for (DisplayData display : displays) {
                Object value = display.getData(MIMEType.TEXT_MARKDOWN);
                if (value == null)
                    value = display.getData(MIMEType.TEXT_PLAIN);
                if (value == null)
                    value = display.getData(MIMEType.TEXT_HTML);
                if (value == null)
                    value = display.getData(MIMEType.IMAGE_SVG);
                if (value != null)
                    texts.add(String.valueOf(value));
            }
            return texts;
        }

        @Override
        public Object evalRaw(String expr) throws Exception {
            evaluated.add(expr);
            return evaluationResult.apply(expr);
        }

        @Override
        public void display(DisplayData data) {
            if (data != null)
                displays.add(data);
        }
    }
}
