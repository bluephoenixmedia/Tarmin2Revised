import json

def update_items():
    path = r"c:\Users\denni\Desktop\Tarmin2\assets\data\items.json"
    try:
        with open(path, 'r') as f:
            data = json.load(f)
        
        count = 0
        for key, template in data.items():
            # Check if this item type is one of the target types
            # Logic: any key containing POTION, SCROLL, WAND, RING? 
            # User said: "wands, scrolls and potions". (rings mentioned in logic in Item.java but not explicitly asked? but rings in my previous search had minLevel 1-21)
            # Let's apply to POTION, SCROLL, WAND variants.
            
            if "POTION" in key or "SCROLL" in key or "WAND" in key:
                 if "variants" in template:
                     for v in template["variants"]:
                         if "minLevel" in v:
                             v["minLevel"] = 1
                             count += 1
                             
        with open(path, 'w') as f:
            json.dump(data, f, indent=2)
            
        print(f"Updated {count} variants to minLevel 1.")
        
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    update_items()
