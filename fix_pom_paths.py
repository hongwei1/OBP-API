import os

with open('pom.xml', 'r') as f:
    content = f.read()

content = content.replace('<sourceDirectory>src/main/scala</sourceDirectory>\n', '')
content = content.replace('<testSourceDirectory>src/test/scala</testSourceDirectory>\n', '')
content = content.replace('<directory>obp-api/target</directory>\n', '')
content = content.replace('''    <resources>
      <resource>
        <directory>src/main/resources</directory>
      </resource>
    </resources>\n''', '')
content = content.replace('<generateGitPropertiesFilename>src/main/resources/git.properties</generateGitPropertiesFilename>', '<generateGitPropertiesFilename>src/main/resources/git.properties</generateGitPropertiesFilename>')
content = content.replace('<source>src/main/java</source>', '<source>src/main/java</source>')

with open('pom.xml', 'w') as f:
    f.write(content)
