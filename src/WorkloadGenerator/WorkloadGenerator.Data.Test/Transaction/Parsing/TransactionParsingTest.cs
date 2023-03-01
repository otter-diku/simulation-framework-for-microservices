namespace WorkloadGenerator.Data.Test.Transaction.Parsing;

public class TransactionParsingTest
{
    [Test]
    public void ShouldParse()
    {
        var json =
            """
                "id": "some-id",
                "arguments": [ 
                    {
                        "name": "arg1",
                        "type": "number"
                    },
                    {
                        "name": "arg2",
                        "type": "string"
                    }
                ],
                "dynamicVariables": [
                    {
                        "name": "var1",
                        "type": "guid"
                    } 
                ],
                "operations": [ 
                    {
                        "operationReferenceId": "some-op-ref-id-1",
                        "id": "first-transaction-op-id",
                        "providedValues": [
                            {
                                "key": "arg1",
                                "value": "{{arg1}}"
                            }
                        ]
                    },
                    {
                        "operationReferenceId": "some-op-ref-id-2",
                        "id": "second-transaction-op-id",
                        "providedValues": [
                            {
                                "key": "arg42",
                                "value": "@@first-transaction-op-id.payload.some-return-value-key@@"
                            }
                        ]
                    }
                ]
            """;
    }
}

/*
 public class TransactionInput
{
    public string Id { get; set; }
    public Argument[]? Arguments { get; set; }
    public DynamicVariable?[] DynamicVariables { get; set; }
    public List<Operation> Operations { get; set; }

    public class Operation
    {
        public string OperationReferenceId { get; set; }

        public string Id { get; set; }

        public ProvidedValue[] ProvidedValues { get; set; }

        public class ProvidedValue
        {
            public string Key { get; set; }
            public object Value { get; set; }
        }
    }
}
 */