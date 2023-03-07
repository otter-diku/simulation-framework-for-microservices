using Data.Model;

namespace DataGenerator.Model;

public class BasketRequest
{
    public string buyerId { get; set; }
    public List<BasketItem> items { get; set; }
}