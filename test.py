import urllib.request
import re

url = 'http://172.16.50.7/DHAKA-FLIX-7/English%20Movies/%282003%29/Master%20and%20Commander-The%20Far%20Side%20of%20the%20World%20%282003%29%20720p/'
html = urllib.request.urlopen(url).read().decode('utf-8')
links = re.findall(r'href="([^"]+)"', html)
print(links)
