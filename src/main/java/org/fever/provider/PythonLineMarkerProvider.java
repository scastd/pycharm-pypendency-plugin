package org.fever.provider;

import com.intellij.codeInsight.daemon.GutterName;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyUtil;
import org.fever.YamlArgumentResolver;
import org.fever.fileresolver.DependencyInjectionFileResolverByIdentifier;
import org.fever.utils.IconCreator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PythonLineMarkerProvider extends LineMarkerProviderDescriptor {
    private static final Icon ICON = IconCreator.create("icons/goToDI.svg");

    @Override
    public @Nullable @GutterName String getName() {
        return "Go to dependency injection";
    }

    @Override
    public @Nullable Icon getIcon() {
        return ICON;
    }

    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement psiElement) {
        if (psiElement instanceof PyClass pyClass) {
            return createLineMarkerInfoForClassElement(pyClass);
        }

        if (psiElement instanceof PyParameter pyParameter && !pyParameter.isSelf()) {
            PyFunction enclosingFunction = PsiTreeUtil.getParentOfType(pyParameter, PyFunction.class);

            if (PyUtil.isInitMethod(enclosingFunction)) {
                PyClass containingClass = enclosingFunction.getContainingClass();
                assert containingClass != null;
                String classFqn = containingClass.getQualifiedName();

                if (classFqn == null) {
                    return null;
                }

                PsiElement element = YamlArgumentResolver.findArgumentDeclaration(pyParameter);
                if (element == null) {
                    return null;
                }

                return NavigationGutterIconBuilder
                        .create(ICON)
                        .setTarget(element)
                        .setTooltipText("Navigate to dependency injection file")
                        .setAlignment(GutterIconRenderer.Alignment.CENTER)
                        .createLineMarkerInfo(pyParameter);
            }
        }

        return null;
    }

    private LineMarkerInfo<?> createLineMarkerInfoForClassElement(@NotNull PyClass pyClass) {
        // Todo: make this work to move cursor to the first line (base yaml fqn)
        String classFqn = pyClass.getQualifiedName();
        if (classFqn == null) {
            return null;
        }

        PsiFile diFile = DependencyInjectionFileResolverByIdentifier.resolve(pyClass.getManager(), classFqn);
        if (diFile == null) {
            return null;
        }

        return NavigationGutterIconBuilder
                .create(ICON)
                .setTarget(diFile)
                .setTooltipText("Navigate to dependency injection file")
                .setAlignment(GutterIconRenderer.Alignment.CENTER)
                .createLineMarkerInfo(pyClass);
    }
}
