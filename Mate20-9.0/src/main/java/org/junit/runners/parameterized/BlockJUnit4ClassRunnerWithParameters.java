package org.junit.runners.parameterized;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Parameterized;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

public class BlockJUnit4ClassRunnerWithParameters extends BlockJUnit4ClassRunner {
    private final String name;
    private final Object[] parameters;

    public BlockJUnit4ClassRunnerWithParameters(TestWithParameters test) throws InitializationError {
        super(test.getTestClass().getJavaClass());
        this.parameters = test.getParameters().toArray(new Object[test.getParameters().size()]);
        this.name = test.getName();
    }

    public Object createTest() throws Exception {
        if (fieldsAreAnnotated()) {
            return createTestUsingFieldInjection();
        }
        return createTestUsingConstructorInjection();
    }

    private Object createTestUsingConstructorInjection() throws Exception {
        return getTestClass().getOnlyConstructor().newInstance(this.parameters);
    }

    private Object createTestUsingFieldInjection() throws Exception {
        List<FrameworkField> annotatedFieldsByParameter = getAnnotatedFieldsByParameter();
        if (annotatedFieldsByParameter.size() == this.parameters.length) {
            Object testClassInstance = getTestClass().getJavaClass().newInstance();
            for (FrameworkField each : annotatedFieldsByParameter) {
                Field field = each.getField();
                int index = ((Parameterized.Parameter) field.getAnnotation(Parameterized.Parameter.class)).value();
                try {
                    field.set(testClassInstance, this.parameters[index]);
                } catch (IllegalArgumentException iare) {
                    throw new Exception(getTestClass().getName() + ": Trying to set " + field.getName() + " with the value " + this.parameters[index] + " that is not the right type (" + this.parameters[index].getClass().getSimpleName() + " instead of " + field.getType().getSimpleName() + ").", iare);
                }
            }
            return testClassInstance;
        }
        throw new Exception("Wrong number of parameters and @Parameter fields. @Parameter fields counted: " + annotatedFieldsByParameter.size() + ", available parameters: " + this.parameters.length + ".");
    }

    /* access modifiers changed from: protected */
    public String getName() {
        return this.name;
    }

    /* access modifiers changed from: protected */
    public String testName(FrameworkMethod method) {
        return method.getName() + getName();
    }

    /* access modifiers changed from: protected */
    public void validateConstructor(List<Throwable> errors) {
        validateOnlyOneConstructor(errors);
        if (fieldsAreAnnotated()) {
            validateZeroArgConstructor(errors);
        }
    }

    /* access modifiers changed from: protected */
    public void validateFields(List<Throwable> errors) {
        super.validateFields(errors);
        if (fieldsAreAnnotated()) {
            List<FrameworkField> annotatedFieldsByParameter = getAnnotatedFieldsByParameter();
            int[] usedIndices = new int[annotatedFieldsByParameter.size()];
            for (FrameworkField each : annotatedFieldsByParameter) {
                int index = ((Parameterized.Parameter) each.getField().getAnnotation(Parameterized.Parameter.class)).value();
                if (index < 0 || index > annotatedFieldsByParameter.size() - 1) {
                    errors.add(new Exception("Invalid @Parameter value: " + index + ". @Parameter fields counted: " + annotatedFieldsByParameter.size() + ". Please use an index between 0 and " + (annotatedFieldsByParameter.size() - 1) + "."));
                } else {
                    usedIndices[index] = usedIndices[index] + 1;
                }
            }
            for (int numberOfUse : usedIndices) {
                if (numberOfUse == 0) {
                    errors.add(new Exception("@Parameter(" + index + ") is never used."));
                } else if (numberOfUse > 1) {
                    errors.add(new Exception("@Parameter(" + index + ") is used more than once (" + numberOfUse + ")."));
                }
            }
        }
    }

    /* access modifiers changed from: protected */
    public Statement classBlock(RunNotifier notifier) {
        return childrenInvoker(notifier);
    }

    /* access modifiers changed from: protected */
    public Annotation[] getRunnerAnnotations() {
        return new Annotation[0];
    }

    private List<FrameworkField> getAnnotatedFieldsByParameter() {
        return getTestClass().getAnnotatedFields(Parameterized.Parameter.class);
    }

    private boolean fieldsAreAnnotated() {
        return !getAnnotatedFieldsByParameter().isEmpty();
    }
}
