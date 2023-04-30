package org.myorg.flinkinvariants.invariantlanguage;

public class TranslationResult {
    private int numberOfSyntaxErrors;
    private boolean semanticAnalysisFailed;

    public TranslationResult(int syntaxErrors, boolean _semanticAnalysisFailed) {
        numberOfSyntaxErrors = syntaxErrors;
        semanticAnalysisFailed = _semanticAnalysisFailed;
    }

    public boolean isSemanticAnalysisFailed() {
        return semanticAnalysisFailed;
    }

    public int getNumberOfSyntaxErrors() {
        return numberOfSyntaxErrors;
    }
}
