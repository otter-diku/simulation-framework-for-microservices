grammar Invariants;

invariant: eventDefinition* query;

query
   : 'EVENT SEQ' '(' events ')' '\n'?
     ('WITHIN' time)? '\n'?
     ('WHERE' where_clause)? '\n'?
     'INVARIANT' invariant_clause
   ;

invariant_clause
  : where_clause ('WITHIN' time)?
  | 'WITHIN' time
  ;

eventDefinition: eventType eventId
                 'topic:' topic
                 'schema:' schema '\n'?
                 ;
eventType: IDENTIFIER;
eventId: IDENTIFIER;
topic: IDENTIFIER;
schema: '{' IDENTIFIER (',' IDENTIFIER)* '}';

events
  : event (',' event)*
  ;

event
  : eventAtom
  | orOperator
  ;

orOperator: '(' event '|' event ')';

regexOp
  : '*'
  | '+'
  ;

eventAtom
  : negEvent regexOp?
  | wildcard regexOp?
  | eventId regexOp?
  ;
negEvent: '[!' eventId (',' eventId)* ']';
wildcard: '?';


where_clause: equality (OP equality)*;
equality: quantity EQ_OP quantity;
quantity
    : qualifiedName
    | atom
    ;

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