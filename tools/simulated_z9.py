#!/usr/bin/env python3
"""Small Nikon-Z9-like FTP client for testing Z9 Tether. Python stdlib only."""
import argparse, socket, re, pathlib, base64
JPEG = base64.b64decode('/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAX/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAH/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAEFAqf/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAEDAQE/AT//xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAECAQE/AT//xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAY/Aqf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAE/IQ//2gAMAwEAAgADAAAAEP/EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8Qf//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8Qf//EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEAAT8Qf//Z')
def run(host, port, name):
    with socket.create_connection((host, port), 10) as c:
        f=c.makefile('rwb', buffering=0)
        def cmd(s):
            f.write((s+'\r\n').encode()); return f.readline().decode(errors='replace').strip()
        print(cmd(''))
        print(cmd('USER nikon')); print(cmd('PASS z9tether'))
        pasv=cmd('PASV'); print(pasv)
        nums=list(map(int,re.search(r'\(([^)]*)\)',pasv).group(1).split(',')))
        data_port=nums[-2]*256+nums[-1]
        with socket.create_connection((host,data_port),10) as d:
            print(cmd('STOR '+name))
            d.sendall(JPEG)
        print(f.readline().decode().strip()); print(cmd('QUIT'))
        print(f'Uploaded {len(JPEG)} bytes as {name}')
if __name__=='__main__':
    ap=argparse.ArgumentParser(); ap.add_argument('host'); ap.add_argument('--port',type=int,default=2121); ap.add_argument('--name',default='DSC_SIM_Z9_0001.JPG'); a=ap.parse_args(); run(a.host,a.port,a.name)
