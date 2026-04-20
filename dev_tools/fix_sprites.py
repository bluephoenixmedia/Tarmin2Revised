import json
import os
import glob

def fix_file(path):
    print(f"Fixing {os.path.basename(path)}...")
    try:
        with open(path, 'r') as f:
            data = json.load(f)
            
        fixes = 0
        for key, entry in data.items():
            if "spriteData" in entry:
                sprites = entry["spriteData"]
                if not isinstance(sprites, list):
                    continue
                
                new_sprites = []
                for i, line in enumerate(sprites):
                    if len(line) != 24:
                        print(f"  Fixing {key} index {i}: length {len(line)} -> 24")
                        diff = 24 - len(line)
                        if diff > 0:
                            # Center pad
                            left = diff // 2
                            right = diff - left
                            new_line = ("." * left) + line + ("." * right)
                            new_sprites.append(new_line)
                            fixes += 1
                        else:
                            # Truncate? Should not happen based on issue description (exceptions are usually too short)
                            # But if too long (index out of bounds if checking >24?), EntityRenderer checks <24.
                            # So only short strings cause problems if loop goes to 24.
                            # Actually, EntityRenderer loop goes to 24.
                            # if texX < 24.
                            # if string is shorter, charAt throws.
                            # if string is LONGER, it's fine (just ignored).
                            # So only fix SHORT strings.
                             new_sprites.append(line)
                    else:
                        new_sprites.append(line)
                
                entry["spriteData"] = new_sprites
                        
        if fixes > 0:
            print(f"  Applying {fixes} fixes to {os.path.basename(path)}")
            with open(path, 'w') as f:
                json.dump(data, f, indent=2)
        else:
            print(f"  No fixes needed for {os.path.basename(path)}")
            
    except Exception as e:
        print(f"Failed to process {path}: {e}")

def main():
    base_dir = r"c:\Users\denni\Desktop\Tarmin2\assets\data"
    files = glob.glob(os.path.join(base_dir, "*.json"))
    
    for filename in files:
        fix_file(filename)

if __name__ == "__main__":
    main()
