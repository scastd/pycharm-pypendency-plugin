package org.fever.provider;

import com.intellij.codeInsight.daemon.GutterName;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.fever.utils.IconCreator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class YamlLineMarkerProvider extends LineMarkerProviderDescriptor {
    private static final Icon ICON = IconCreator.create("icons/goToSource.svg");
    private static final String FQN_KEY = "fqn";
    private static final String ARGUMENTS_KEY = "args";

    @Override
    public @Nullable @GutterName String getName() {
        return "Go to python class";
    }

    @Override
    public @Nullable Icon getIcon() {
        return ICON;
    }

    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement psiElement) {
        RelatedItemLineMarkerInfo<PsiElement> markerInfo = markerInfoForYamlToPythonConstructorParameter(psiElement);
        if (markerInfo != null) {
            return markerInfo;
        }

        return markerInfoForYamlToPythonClass(psiElement);
    }

    private static @Nullable RelatedItemLineMarkerInfo<PsiElement> markerInfoForYamlToPythonClass(@NotNull PsiElement psiElement) {
        if (psiElement instanceof YAMLKeyValue keyValue && FQN_KEY.equals(keyValue.getKeyText())) {
            if (keyValue.getValue() instanceof YAMLScalar fqnScalar) {
                String classFqn = fqnScalar.getTextValue();
                PyClass targetClass = PyClassNameIndex.findClass(classFqn, psiElement.getProject());

                if (targetClass != null) {
                    assert keyValue.getKey() != null;
                    PsiElement classIdentifier = targetClass.getNameIdentifier();

                    return NavigationGutterIconBuilder
                            .create(ICON)
                            .setTarget(classIdentifier != null ? classIdentifier : targetClass)
                            .setTooltipText("Navigate to Python class")
                            .setAlignment(GutterIconRenderer.Alignment.CENTER)
                            .createLineMarkerInfo(keyValue.getKey());
                }
            }
        }

        return null;
    }

    private @Nullable RelatedItemLineMarkerInfo<PsiElement> markerInfoForYamlToPythonConstructorParameter(@NotNull PsiElement psiElement) {
        if (psiElement instanceof YAMLScalar) {
            YAMLSequenceItem item = PsiTreeUtil.getParentOfType(psiElement, YAMLSequenceItem.class);

            if (item != null) {
                YAMLSequence sequence = PsiTreeUtil.getParentOfType(item, YAMLSequence.class);
                YAMLKeyValue argsKeyValue = PsiTreeUtil.getParentOfType(sequence, YAMLKeyValue.class);

                if (sequence != null && argsKeyValue != null && ARGUMENTS_KEY.equals(argsKeyValue.getKeyText())) {
                    int argumentIndex = sequence.getItems().indexOf(item);
                    YAMLMapping serviceMapping = PsiTreeUtil.getParentOfType(argsKeyValue, YAMLMapping.class);

                    if (serviceMapping != null) {
                        YAMLKeyValue fqnKeyValue = serviceMapping.getKeyValueByKey(FQN_KEY);

                        if (fqnKeyValue != null && fqnKeyValue.getValue() instanceof YAMLScalar fqnScalar) {
                            PyParameter targetParameter = findPythonParameterInConstructor(psiElement.getProject(),
                                                                                           fqnScalar.getTextValue(),
                                                                                           argumentIndex);
                            if (targetParameter != null) {
                                return NavigationGutterIconBuilder
                                        .create(ICON)
                                        .setTarget(targetParameter)
                                        .setTooltipText("Navigate to Python parameter")
                                        .setAlignment(GutterIconRenderer.Alignment.CENTER)
                                        .createLineMarkerInfo(psiElement);
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private PyParameter findPythonParameterInConstructor(@NotNull Project project, @NotNull String classFqn, int parameterIndex) {
        PyClass pyClass = PyClassNameIndex.findClass(classFqn, project);
        if (pyClass == null) {
            return null;
        }

        PyFunction initMethod = pyClass.findMethodByName(
                "__init__", false, TypeEvalContext.userInitiated(project, pyClass.getContainingFile())
        );
        if (initMethod == null) {
            return null;
        }

        List<PyParameter> paramsWithoutSelf = new ArrayList<>();
        for (PyParameter parameter : initMethod.getParameterList().getParameters()) {
            if (!parameter.isSelf()) {
                paramsWithoutSelf.add(parameter);
            }
        }

        if (parameterIndex >= 0 && parameterIndex < paramsWithoutSelf.size()) {
            return paramsWithoutSelf.get(parameterIndex);
        }

        return null;
    }
}
