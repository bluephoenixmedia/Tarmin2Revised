import json

def validate(data, source):
    print(f"Validating {source}...")
    for key, item in data.items():
        sprite = item.get('spriteData')
        if not sprite: continue
        
        for i, line in enumerate(sprite):
            if len(line) != 24:
                print(f"ERROR: [{source}] {key} Line {i} has length {len(line)}: '{line}'")


# Check weapons
with open('assets/data/weapons.json', 'r') as f:
    validate(json.load(f), "WEAPON")

# Check monsters
with open('assets/data/monsters.json', 'r') as f:
    data = json.load(f)
    if "OGRE" in data:
        sprite = data["OGRE"].get("spriteData")
        for i, line in enumerate(sprite):
            if len(line) != 24:
                print(f"OGRE Line {i} Len {len(line)}: '{line}'")
