import io, re, sys

def strip(s: str) -> str:
    s = re.sub(r'""".*?"""', '', s, flags=re.S)
    s = re.sub(r'"(\\.|[^"\\])*"', '', s)
    s = re.sub(r"'(\\.|[^'\\])*'", '', s)
    s = re.sub(r'//.*', '', s)
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
    return s

for p in sys.argv[1:]:
    s = strip(io.open(p, encoding='utf-8').read())
    print(p, 'braces:', s.count('{') - s.count('}'), 'parens:', s.count('(') - s.count(')'))
