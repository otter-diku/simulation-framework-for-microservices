grammar Invariants;

invariant: eventDefinition* query EOF;

query
   : 'SEQ' '(' events ')' '\n'?
     ('WITHIN' time)? '\n'?
     ('WHERE' where_clause)? '\n'?
     on_full_match '\n'?
     (on_prefix_match '\n'?)*
   | 'SEQ' '(' events ')' '\n'?
     ('WITHIN' time)? '\n'?
     ('WHERE' where_clause)? '\n'?
     (on_prefix_match '\n'?)+
   ;

on_full_match: 'ON FULL MATCH' invariant_clause;
on_prefix_match: 'ON PREFIX MATCH' prefix invariant_clause;

prefix
    : default_prefix
    | '(' events ')';

default_prefix: 'DEFAULT';

invariant_clause
  : lpar term rpar (and lpar term rpar)*
  | BOOL
  ;

eventDefinition: eventType eventId
                 'topic:' topic
                 'schema:' schema '\n'?
                 ;
eventType: IDENTIFIER;
eventId: IDENTIFIER;
topic: IDENTIFIER;
schema: '{' schemaMember (',' schemaMember)* '}';

schemaMember: IDENTIFIER ':' memberType;

memberType: 'number' | 'string' | 'bool';

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
  : lpar term rpar (and lpar term rpar)*
  ;

term
    : equality
    | term and term
    | term or term
    //| term op term
    | lpar term rpar
    ;


/*cond
  : equality
  | cond OP cond
//  | lpar cond rpar
  ;*/


lpar: '(';
rpar: ')';

equality
  : quantity EQ_OP quantity;

quantity
    : atom
    | qualifiedName
    ;

atom
    : BOOL
    | INT
    | STRING
    ;


qualifiedName
    : IDENTIFIER ('.' IDENTIFIER)*
    ;

time: INT TIME;

//OP: 'AND' | 'OR';
and: 'AND';
or: 'OR';

EQ_OP: '=' | '!=' | '<' | '<=' | '>' | '>=';
INT: '-'?[0-9]+;
STRING: '\'' IDENTIFIER '\'';
TIME
    : 'msec'
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