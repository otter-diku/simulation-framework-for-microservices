grammar Invariants;

invariant
      : 'EVENTS' '(' events ')' '\n'?
        ('WHERE' where_clause)? '\n'?
        ('ORDERING' orderings)? '\n'?
        'WITHIN' INT TIME
      | 'EVENTS' '(' events ')' '\n'?
        ('WHERE' where_clause)? '\n'?
        ('ORDERING' orderings)? '\n'?
        'WINDOW' INT TIME
        ;

events: event (',' event)*;
event: eventSchema eventId;
eventSchema: IDENTIFIER;
eventId: TEXT;

where_clause: equality (OP equality)*;
equality: qualifiedName '=' qualifiedName;


orderings: ordering (OP ordering)*;
ordering: '<' TEXT (',' TEXT)* '>';

qualifiedName
    : TEXT ('.' identifier)*
    ;

OP: 'AND' | 'OR';
INT: [0-9]+;
IDENTIFIER
    : (LETTER | DIGIT | '_')+
    ;
identifier
    : (TEXT | '_')+
    ;
TIME
    : 'sec'
    | 'min'
    | 'hour'
    ;
DIGIT: [0-9];
LETTER: [A-Z];
TEXT: [a-z]+;



WS: [ \t\n\r\f]+ -> skip ;