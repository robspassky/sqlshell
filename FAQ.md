title: SQLShell FAQ
%%%

1. How do I get GNU Readline to work?

   Mac OS X:

> Follow the instructions at the following web page:
> http://blog.toonetown.com/2006/07/java-readline-on-mac-os-x-update.html
>
> This approach actually uses the Editline library, not GNU Readline.
> To customize the bindings, see *editrc*(5)

   Ubuntu:

> - Install the libreadline-java package
> - Ensure that /usr/share/java/libreadline-java.jar is in your CLASSPATH
> - Ensure that `LD_LIBRARY_PATH` contains `/usr/lib/jni`
> - If you want to use Editline, instead of Readline, ensure that `libedit2`
>   is installed. Then, use the *sqlshell* "-r editline" argument to
>   override SQLShell's default readline library search.
