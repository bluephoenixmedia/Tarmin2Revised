import json
import os

def main():
    # Path to weapons.json
    weapons_json_path = 'assets/data/weapons.json'
    
    # Path to images directory
    images_dir = 'assets/images/weapons'
    
    # Read the missing weapons list to know which keys to update
    with open('missing_weapons.txt', 'r') as f:
        lines = f.readlines()
        
    items_to_update = []
    for line in lines:
        # Format is "KEY (Name)"
        line = line.strip()
        if not line: continue
        parts = line.split('(')
        key = parts[0].strip()
        items_to_update.append(key)

    print(f"Scanning {len(items_to_update)} items from missing list...")

    # Load JSON
    with open(weapons_json_path, 'r') as f:
        data = json.load(f)

    updated_count = 0
    
    for key in items_to_update:
        if key not in data:
            print(f"Warning: Key {key} not found in weapons.json")
            continue
            
        filename = key.lower() + ".png"
        image_path_abs = os.path.join(images_dir, filename)
        
        # Verify file exists
        if os.path.exists(image_path_abs):
            current_path = data[key].get('texturePath')
            new_path = f"images/weapons/{filename}"
            
            # Update texture path relative to assets
            if current_path != new_path:
                print(f"Updating {key}: {current_path} -> {new_path}")
                data[key]['texturePath'] = new_path
                updated_count += 1
            else:
                pass # Already correct
        else:
            # print(f"Warning: Image file not found for {key}: {image_path_abs}")
            pass

    # Write back to JSON
    if updated_count > 0:
        with open(weapons_json_path, 'w') as f:
            json.dump(data, f, indent='\t') # Use tab indentation to match original
        print(f"Success! Updated {updated_count} items in weapons.json")
    else:
        print("No new updates found.")

if __name__ == "__main__":
    main()
