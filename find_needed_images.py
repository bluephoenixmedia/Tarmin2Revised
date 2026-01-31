import json
import os

def main():
    with open('missing_weapons.txt', 'r') as f:
        lines = f.readlines()

    with open('assets/data/weapons.json', 'r') as f:
        weapons_data = json.load(f)

    # Base assets path
    assets_dir = 'assets'
    
    needed = []

    for line in lines:
        line = line.strip()
        if not line:
            continue
        
        # Parse "KEY (Name)"
        # Some names have parens, so split by first space
        parts = line.split(' ', 1)
        key = parts[0]
        name = parts[1].strip('()') if len(parts) > 1 else key

        if key not in weapons_data:
            print(f"WARNING: Key {key} not found in weapons.json. Assuming generic path.")
            # If not in JSON, assume we need it and use a default name
            # default name: lowercase key + .png
            filename = key.lower() + ".png"
            needed.append({
                'key': key,
                'name': name,
                'path': os.path.join(assets_dir, 'images', 'weapons', filename),
                'filename': filename
            })
            continue
        
        weapon_entry = weapons_data[key]
        texture_path = weapon_entry.get('texturePath')
        
        if not texture_path:
            print(f"WARNING: Key {key} has no texturePath")
            continue
            
        full_path = os.path.join(assets_dir, texture_path)
        
        # Check existence
        if not os.path.exists(full_path):
            needed.append({
                'key': key,
                'name': name,
                'path': full_path,
                'filename': os.path.basename(full_path)
            })
        else:
            # print(f"SKIP: {key} -> {texture_path} exists")
            pass

    with open('needed_images.json', 'w') as f:
        json.dump(needed, f, indent=2)
    
    print(f"Found {len(needed)} needed images.")

if __name__ == '__main__':
    main()
