import os

filepath = 'e:/app zad/app/src/main/java/com/example/ui/screens/HomeScreen.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('remember { mutableStateOf(stringResource(R.string.loading)) }', 'remember { mutableStateOf("...") }')
text = text.replace('?: stringResource(R.string.new_user)', '?: "..."')

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(text)
