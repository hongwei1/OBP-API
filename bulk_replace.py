import os
import re

def process_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception:
        return False
        
    new_content = content
    
    # Replace src/ with src/
    new_content = re.sub(r'\bobp-api/src/', 'src/', new_content)
    # Replace target/ with target/
    new_content = re.sub(r'\bobp-api/target/', 'target/', new_content)
    # Replace pom.xml with pom.xml
    new_content = re.sub(r'\bobp-api/pom.xml', 'pom.xml', new_content)
    # Replace compile-api.log with compile-api.log
    new_content = re.sub(r'\bobp-api/compile-api.log', 'compile-api.log', new_content)
    
    if new_content != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Updated {filepath}")
        return True
    return False

exclude_dirs = {'.git', '.bloop', '.metals', '.idea', 'target', 'obp-api'}

for root, dirs, files in os.walk('.'):
    dirs[:] = [d for d in dirs if d not in exclude_dirs]
    for file in files:
        if file.endswith(('.sh', '.md', '.py', '.yml', '.yaml', '.txt', '.scala', '.properties', 'Dockerfile', 'Dockerfile.local', 'Dockerfile.dev', 'Dockerfile_PreBuild')):
            process_file(os.path.join(root, file))

# specific paths for external notes like ownCmds.md
external_files = [
    "/Users/zhanghongwei/Documents/GitHub-Tower/DayDayUp/myIdeas/20260703LearnDocker/ownCmds.md",
    "/Users/zhanghongwei/Documents/GitHub-Tower/DayDayUp/myIdeas/20260703LearnDocker/docker_learning_roadmap.md"
]
for ef in external_files:
    if os.path.exists(ef):
        process_file(ef)

