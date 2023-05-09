package org.myorg.flinkinvariants.invariantlanguage;

import org.apache.flink.api.java.tuple.Tuple2;
import org.myorg.invariants.parser.InvariantsParser;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class TranslationResult {
    private int numberOfSyntaxErrors;
    private boolean semanticAnalysisFailed;
    public Set<String> topics;
    public Set<String> relevantEventTypes;
    public Map<String, String> id2Type;
    public Map<String, Map<String, String>> schemata;
    public EventSequence sequence;
    public List<InvariantsParser.TermContext> whereClauseTerms;

    public Optional<Tuple2<Integer, String>> within;
    public Optional<InvariantsParser.Invariant_clauseContext> onFullMatch;
    public List<Tuple2<InvariantsParser.PrefixContext, InvariantsParser.Invariant_clauseContext>> onPartialMatch;

    public TranslationResult(int numberOfSyntaxErrors,
                             boolean semanticAnalysisFailed,
                             Set<String> topics,
                             Set<String> relevantEventTypes,
                             Map<String, String> id2Type,
                             Map<String, Map<String, String>> schemata,
                             EventSequence sequence,
                             List<InvariantsParser.TermContext> whereClauseTerms,
							 Optional<Tuple2<Integer, String>> within,
                             Optional<InvariantsParser.Invariant_clauseContext> onFullMatch,
                             List<Tuple2<InvariantsParser.PrefixContext, InvariantsParser.Invariant_clauseContext>> onPartialMatch) {
        this.numberOfSyntaxErrors = numberOfSyntaxErrors;
        this.semanticAnalysisFailed = semanticAnalysisFailed;
        this.topics = topics;
        this.relevantEventTypes = relevantEventTypes;
        this.id2Type = id2Type;
        this.schemata = schemata;
        this.sequence = sequence;
        this.whereClauseTerms = whereClauseTerms;
        this.within = within;
        this.onFullMatch = onFullMatch;
        this.onPartialMatch = onPartialMatch;
    }

    public boolean isSemanticAnalysisFailed() {
        return semanticAnalysisFailed;
    }

    public int getNumberOfSyntaxErrors() {
        return numberOfSyntaxErrors;
    }
}
