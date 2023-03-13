using Data.Model;

namespace EshopData
{
    public static class DataGenerator
    {
        const string Numbers = "0123456789";
        const string Alphanumeric = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        const int Offset = 0; // temporal during testing to not reinitialize application always

        public static int LargestGeneratedCatalogItemId = 10;

        public static List<CatalogItem> GenerateCatalogItems(int NumberOfItems, int MIN_ITEM_QTY, int MAX_ITEM_QTY)
        {
            List<CatalogItem> items = new List<CatalogItem>(NumberOfItems);

            for (int i = 1 + Offset; i <= NumberOfItems + Offset; i++)
            {
                CatalogItem item = new CatalogItem
                {
                    Id = i,
                    Name = RandomString(8, Alphanumeric),
                    Description = RandomString(8, Alphanumeric),
                    Price = Math.Ceiling((decimal)(new Random().NextDouble() * 10000)) / 100,
                    PictureFileName = "",
                    PictureUri = "",
                    CatalogTypeId = i,

                    CatalogType = new CatalogType
                    {
                        Id = i,
                        Type = RandomString(8, Alphanumeric)
                    },

                    CatalogBrandId = i,

                    CatalogBrand = new CatalogBrand
                    {
                        Id = i,
                        Brand = RandomString(8, Alphanumeric)
                    },

                    AvailableStock = new Random().Next(MIN_ITEM_QTY, MAX_ITEM_QTY),
                    RestockThreshold = MIN_ITEM_QTY,
                    MaxStockThreshold = MAX_ITEM_QTY,
                    OnReorder = false
                };

                items.Add(item);
            }

            return items;
        }

        public static CatalogItem GenerateCatalogItem(int MIN_ITEM_QTY, int MAX_ITEM_QTY)
        {
            CatalogItem item = new CatalogItem
            {
                Id = LargestGeneratedCatalogItemId,
                Name = RandomString(8, Alphanumeric),
                Description = RandomString(8, Alphanumeric),
                Price = Math.Ceiling((decimal)(new Random().NextDouble() * 10000)) / 100,
                PictureFileName = "",
                PictureUri = "",
                CatalogTypeId = 1,

                CatalogType = new CatalogType
                {
                    Id = 1,
                    Type = RandomString(8, Alphanumeric)
                },

                CatalogBrandId = 1,

                CatalogBrand = new CatalogBrand
                {
                    Id = 1,
                    Brand = RandomString(8, Alphanumeric)
                },

                AvailableStock = new Random().Next(MIN_ITEM_QTY, MAX_ITEM_QTY),
                RestockThreshold = MIN_ITEM_QTY,
                MaxStockThreshold = MAX_ITEM_QTY,
                OnReorder = false
            };

            //  keep track of existing catalogItemIds such that basket transactions can access them
            LargestGeneratedCatalogItemId += 1;
            return item;
        }


        private static List<BasketItem> GenerateBasketItems(int NumberOfItems)
        {
            throw new Exception("Using GenerateBasketItems, not good as items are different than the catalog");
            List<BasketItem> items = new List<BasketItem>(NumberOfItems);

            for (int i = 0; i < NumberOfItems; i++)
            {
                BasketItem item = new BasketItem();

                item.ProductId = i.ToString();
                item.ProductName = RandomString(8, Alphanumeric);

                item.UnitPrice = Math.Ceiling((decimal)(new Random().NextDouble() * 10000)) / 100;
                item.OldUnitPrice = item.UnitPrice;
                item.Quantity = 10;
                item.PictureUrl = "";

                items.Add(item);
            }

            return items;
        }

        public static List<BasketItem> GenerateBasketForExistingItems(int NumberOfItems, List<CatalogItem> catalogItems)
        {
            List<BasketItem> basket = new List<BasketItem>(NumberOfItems);

            for (int i = 0; i < NumberOfItems; i++)
            {
                CatalogItem catalogItem = catalogItems[new Random().Next(catalogItems.Count)];
                BasketItem item = new BasketItem();

                item.ProductId = catalogItem.Id.ToString();
                item.ProductName = catalogItem.Name;

                item.UnitPrice = catalogItem.Price;
                item.OldUnitPrice = catalogItem.Price;
                item.Quantity = new Random().Next(1, catalogItem.AvailableStock);
                item.PictureUrl = null;

                basket.Add(item);
            }

            return basket;
        }

        public static List<ApplicationUser> GenerateCustomers(int NumberOfCustomers)
        {
            List<ApplicationUser> users = new List<ApplicationUser>(NumberOfCustomers);

            for (int i = 0; i < NumberOfCustomers; i++)
            {
                ApplicationUser user = new ApplicationUser();
                user.CardNumber = RandomString(16, Numbers); // needs to be between 12 and 19
                user.SecurityNumber = RandomString(3, Numbers); // needs to have length 3
                user.CardExpiration = DateTime.Now.AddYears(10);

                user.CardHolderName = RandomString(8, Alphanumeric);
                user.CardType = new Random().Next(1, 3);

                user.Street = RandomString(8, Alphanumeric);
                user.City = RandomString(8, Alphanumeric);
                user.State = RandomString(8, Alphanumeric);
                user.Country = RandomString(8, Alphanumeric);
                user.ZipCode = RandomString(8, Numbers);
                user.Name = RandomString(8, Alphanumeric);
                user.LastName = RandomString(8, Alphanumeric);

                users.Add(user);
            }


            return users;
        }

        public static List<string> GenerateGuids(int numberOfIds)
        {
            List<string> guids = new List<string>();
            for (int i = 0; i < numberOfIds; i++)
            {
                guids.Add(Guid.NewGuid().ToString());
            }

            return guids;
        }

        private static string RandomString(int length, string chars)
        {
            var random = new Random();
            return new string(Enumerable.Repeat(chars, length)
                .Select(s => s[random.Next(s.Length)]).ToArray());
        }
    }
}