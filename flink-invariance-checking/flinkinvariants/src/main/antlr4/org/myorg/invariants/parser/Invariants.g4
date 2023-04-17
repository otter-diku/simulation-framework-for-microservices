grammar Invariants;

invariant: eventDefinition* query;

query
   : 'EVENT SEQ' '(' events ')' '\n'?
     ('WHERE' where_clause)? '\n'?
     ('ORDERING' orderings)? '\n'?
     'WITHIN' time
   | 'EVENTS' '(' events ')' '\n'?
     ('WHERE' where_clause)? '\n'?
     ('ORDERING' orderings)? '\n'?
     'WINDOW' time;

eventDefinition: eventName
                 'topic:' topic
                 'schema:' schema
                 ;
eventName: IDENTIFIER;
topic: IDENTIFIER;
schema: '{' IDENTIFIER (',' IDENTIFIER)* '}';


events: event (',' event)*;
event: eventSchema eventId;
eventSchema: IDENTIFIER;
eventId: IDENTIFIER;

where_clause: equality (OP equality)*;
equality: quantity EQ_OP quantity;
quantity
    : qualifiedName
    | atom
    ;


orderings: ordering (OP ordering)*;
ordering: '<' IDENTIFIER (',' IDENTIFIER)* '>';

atom
    : BOOL
    | INT
    ;

qualifiedName
    : IDENTIFIER ('.' IDENTIFIER)*
    ;

time: INT TIME;


OP: 'AND' | 'OR';
EQ_OP: '=' | '!=' | '<' | '<=' | '>' | '>=';
INT: [0-9]+;
TIME
    : 'milli'
    | 'sec'
    | 'min'
    | 'hour'
    ;
BOOL: 'true' | 'false';
IDENTIFIER
    : (LETTER | DIGIT | '_' | '-')+
    ;
DIGIT: [0-9];
LETTER: [A-Z] | [a-z];
TEXT: [a-z]+;

WS: [ \t\n\r\f]+ -> skip ;