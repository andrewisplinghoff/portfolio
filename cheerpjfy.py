import os
import subprocess
import concurrent.futures


working_dir = os.path.dirname(__file__)


def worker(args):
    dep = args
    if os.path.isfile(f'{dep}.js'):
        print(f'File {dep}.js already exists, skipping compilation')
    else:
        cmd = ['/usr/bin/python3.6', '/home/andre/cheerpj_1.3/cheerpjfy.py', '--deps-file',
               os.path.join(working_dir, 'jars.txt'), dep]
        print(cmd)
        subprocess.call(cmd, cwd=working_dir)
    return dep


def main():
    with open('jars.txt') as f:
        deps = [line[:-1] for line in f.readlines()]

    for jar in ['/home/andre/portfolio-dev/.metadata/.plugins/org.eclipse.pde.core/.bundle_pool/plugins/org.eclipse.osgi_3.13.100.v20180827-1536.jar']:
        worker(jar)

    # with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
    #    for index, dep in enumerate(executor.map(worker, deps)):
    #        print(f'Finished processing {dep} - {index+1}/{len(deps)} ({round((index+1)/len(deps)*100, 2)}%)')



if __name__ == '__main__':
    main()
