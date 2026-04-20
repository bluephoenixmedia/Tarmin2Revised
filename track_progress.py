import os

def main():
    missing_file = 'missing_weapons.txt'
    temp_dir = 'temp_weapons'
    output_file = 'weapon_generation_progress.txt'

    # Read missing weapons
    with open(missing_file, 'r') as f:
        lines = f.readlines()

    missing_weapons = []
    for line in lines:
        line = line.strip()
        if not line: continue
        # Format: KEY (Name)
        parts = line.split(' (', 1)
        key = parts[0]
        name = parts[1][:-1] if len(parts) > 1 else key
        missing_weapons.append((key, name))

    # Check temp_weapons
    existing_files = set(os.listdir(temp_dir))
    
    processed = []
    remaining = []

    for key, name in missing_weapons:
        filename = key.lower() + ".png"
        if filename in existing_files:
            processed.append((key, name))
        else:
            remaining.append((key, name))

    # Write progress file
    with open(output_file, 'w') as f:
        f.write(f"Total Missing Initially: {len(missing_weapons)}\n")
        f.write(f"Processed: {len(processed)}\n")
        f.write(f"Remaining: {len(remaining)}\n\n")
        
        f.write("=== PROCESSED (Found in temp_weapons) ===\n")
        for key, name in processed:
            f.write(f"[x] {key} ({name})\n")
            
        f.write("\n=== REMAINING ===\n")
        for key, name in remaining:
            f.write(f"[ ] {key} ({name})\n")

    print(f"Progress report written to {output_file}")
    print(f"Processed: {len(processed)}, Remaining: {len(remaining)}")
    
    # Print next 5
    print("NEXT 5 TO GENERATE:")
    for i in range(min(5, len(remaining))):
        key, name = remaining[i]
        print(f"{key}|{name}")

if __name__ == '__main__':
    main()
