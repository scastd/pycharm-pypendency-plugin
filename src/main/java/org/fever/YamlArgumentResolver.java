package org.fever;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import org.fever.fileresolver.DependencyInjectionFileResolverByIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.*;

import java.util.List;

public class YamlArgumentResolver {
    private static final String ARGUMENTS_KEY = "args";
    private static final String FQN_KEY = "fqn";

    @Nullable
    public static PsiElement findArgumentDeclaration(@NotNull PyParameter parameter) {
        PyFunction initMethod = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
        if (initMethod == null) {
            return null;
        }

        PyClass pyClass = initMethod.getContainingClass();
        if (pyClass == null) {
            return null;
        }

        String classFqn = pyClass.getQualifiedName();
        if (classFqn == null) {
            return null;
        }

        int parameterIndex = -1;
        int currentIndex = 0;
        for (PyParameter p : initMethod.getParameterList().getParameters()) {
            if (!p.isSelf()) {
                if (p.equals(parameter)) {
                    parameterIndex = currentIndex;
                    break;
                }

                currentIndex++;
            }
        }

        if (parameterIndex == -1) {
            return null;
        }

        PsiFile file = DependencyInjectionFileResolverByIdentifier.resolve(parameter.getManager(), classFqn);
        if (!(file instanceof YAMLFile yamlFile)) {
            return null;
        }

        YAMLMapping serviceDefinition = findServiceDefinition(yamlFile, classFqn);
        if (serviceDefinition == null) {
            return null;
        }

        YAMLKeyValue keyValueByKey = serviceDefinition.getKeyValueByKey(ARGUMENTS_KEY);
        if (keyValueByKey != null && keyValueByKey.getValue() instanceof YAMLSequence argumentsSequence) {
            List<YAMLSequenceItem> args = argumentsSequence.getItems();

            if (parameterIndex < args.size()) {
                return args.get(parameterIndex).getValue();
            }
        }

        return null;
    }

    private static YAMLMapping findServiceDefinition(@NotNull YAMLFile yamlFile, @NotNull String classFqn) {
        YamlRecursiveElementVisitor psiElementVisitor = new YamlRecursiveElementVisitor(classFqn);
        yamlFile.accept(psiElementVisitor);
        return psiElementVisitor.foundMapping;
    }

    private static class YamlRecursiveElementVisitor extends PsiRecursiveElementVisitor {
        private final String classFqn;
        private YAMLMapping foundMapping = null;

        public YamlRecursiveElementVisitor(String classFqn) {
            this.classFqn = classFqn;
        }

        @Override
        public void visitElement(@NotNull PsiElement element) {
            if (foundMapping != null) {
                return;
            }

            if (element instanceof YAMLKeyValue keyValue) {
                if (FQN_KEY.equals(keyValue.getKeyText()) && keyValue.getValue() instanceof YAMLScalar value) {
                    String yamlValueText = value.getTextValue();

                    if (classFqn.equals(yamlValueText)) {
                        PsiElement parent = keyValue.getParent();

                        if (parent instanceof YAMLMapping) {
                            foundMapping = (YAMLMapping) parent;
                        }
                    }
                }
            }

            if (foundMapping == null) {
                super.visitElement(element);
            }
        }
    }
}
