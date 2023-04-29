grammar Invariants;

invariant: eventDefinition* query;

query
   : 'SEQ' '(' events ')' '\n'?
     'WITHIN' time '\n'?
     ('WHERE' where_clause)? '\n'?
     'ON FULL MATCH' invariant_clause
     ('ON PARTIAL MATCH' invariant_clause)?
   | 'SEQ' '(' events ')' '\n'?
     'WITHIN' time '\n'?
     ('WHERE' where_clause)? '\n'?
     'ON PARTIAL MATCH' invariant_clause
   ;

invariant_clause
  : where_clause
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

orOperator: '(' eventId ('|' eventId)* ')' regexOp?;

regexOp
  : '*'
  | '+'
  ;

negEvent: '!' eventId;

eventAtom
  : negEvent
  | eventId regexOp?;


// (a.id = 42 OR b.id = 42) AND a.price < 42
// cond -> cond AND cond -> ( cond ) AND cond -> ( cond OR cond) AND cond
where_clause
  : cond (OP cond)*
  ;
cond
  : equality
  | '(' cond ')'
  ;


//where_clause
//  : equality (OP equality)*;
equality
  : quantity EQ_OP quantity;

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